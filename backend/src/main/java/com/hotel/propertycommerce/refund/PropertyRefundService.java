package com.hotel.propertycommerce.refund;

import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PropertyRefundService {

    private final PropertyFinancialTransactionRepository transactionRepository;
    private final PropertyRefundRequestRepository requestRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;
    private final Clock clock;

    @Autowired
    public PropertyRefundService(
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyRefundRequestRepository requestRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService) {
        this(transactionRepository, requestRepository, propertyAccessService, auditService, Clock.systemUTC());
    }

    PropertyRefundService(
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyRefundRequestRepository requestRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.requestRepository = requestRepository;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public RefundResult request(RequestCommand command) {
        validate(command);
        PropertyFinancialTransaction original = transactionRepository.findByPublicIdForUpdate(command.transactionPublicId().trim())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(original);
        validateOriginal(original);
        Long hotelId = original.getHotel().getId();
        String idempotencyKey = normalize(command.idempotencyKey(), "idempotencyKey", 160);
        String requestHash = hash(original.getPublicId() + '|' + VndMoney.of(command.amount()).amount().toPlainString()
                + '|' + normalize(command.reason(), "reason", 1000));
        PropertyRefundRequest existing = requestRepository.findByHotelIdAndIdempotencyKey(hotelId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            verifyReplay(existing, requestHash);
            return result(existing, refundableBalance(original), true);
        }

        VndMoney requested = VndMoney.of(command.amount());
        if (requested.amount().signum() <= 0) throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT);
        VndMoney available = availableForNewRequest(original);
        if (requested.amount().compareTo(available.amount()) > 0) {
            throw new FinancialException(FinancialErrorCode.REFUND_EXCEEDS_BALANCE);
        }
        User actor = propertyAccessService.currentUser();
        PropertyRefundRequest refund = PropertyRefundRequest.request(
                original.getHotel(), original, requested, command.reason(), actor,
                idempotencyKey, requestHash, now());
        refund = requestRepository.saveAndFlush(refund);
        audit(refund, actor, null, RefundState.REQUESTED.name(), "Property refund requested",
                command.correlationId(), Map.of("amount", requested.amount(), "remaining", available.subtract(requested).amount()));
        return result(refund, available.subtract(requested), false);
    }

    @Transactional
    public List<RefundResult> requestCancellationRefunds(
            Long reservationId,
            String reason,
            String correlationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId is required.");
        }
        String normalizedReason = normalize(reason, "reason", 1000);
        return transactionRepository.findByReservationIdOrderByOccurredAtAsc(reservationId).stream()
                .filter(this::isCancellationRefundable)
                .filter(transaction -> availableForNewRequest(transaction).amount().signum() > 0)
                .map(transaction -> request(new RequestCommand(
                        transaction.getPublicId(),
                        availableForNewRequest(transaction).amount(),
                        normalizedReason,
                        "reservation-cancellation:" + reservationId + ":" + transaction.getPublicId(),
                        correlationId)))
                .toList();
    }

    @Transactional
    public RefundResult approve(String refundPublicId, String correlationId) {
        PropertyRefundRequest refund = lockedRequest(refundPublicId);
        authorize(refund.getOriginalTransaction());
        User actor = propertyAccessService.currentUser();
        String previous = refund.getStatus().name();
        refund.approve(actor);
        refund = requestRepository.saveAndFlush(refund);
        audit(refund, actor, previous, refund.getStatus().name(), "Property refund approved",
                correlationId, Map.of("amount", refund.getRequestedAmount()));
        return result(refund, refundableBalance(refund.getOriginalTransaction()), false);
    }

    @Transactional(readOnly = true)
    public List<RefundResult> listForProperty(Long propertyId) {
        propertyAccessService.requireAssignedHotel(propertyId);
        return requestRepository.findByHotelIdOrderByRequestedAtDesc(propertyId).stream()
                .map(refund -> result(refund, refundableBalance(refund.getOriginalTransaction()), false))
                .toList();
    }

    @Transactional
    public RefundResult completeSucceeded(String refundPublicId, String providerReference, String correlationId) {
        PropertyRefundRequest refund = lockedRequest(refundPublicId);
        PropertyFinancialTransaction original = transactionRepository.findByPublicIdForUpdate(
                        refund.getOriginalTransaction().getPublicId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        String effectIdentity = "PROPERTY-REFUND:" + refund.getPublicId();
        PropertyFinancialTransaction existing = transactionRepository.findByIdempotencyIdentity(effectIdentity)
                .orElse(null);
        if (existing != null) {
            if (refund.getStatus() != RefundState.SUCCEEDED) {
                refund.markSucceeded(VndMoney.of(existing.getAmount()), existing.getOccurredAt());
                requestRepository.saveAndFlush(refund);
            }
            return result(refund, refundableBalance(original), true);
        }
        if (refund.getStatus() != RefundState.PENDING_PROVIDER) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
        VndMoney refundable = refundableBalance(original);
        if (refund.getRequestedAmount().compareTo(refundable.amount()) > 0) {
            throw new FinancialException(FinancialErrorCode.REFUND_EXCEEDS_BALANCE);
        }
        LocalDateTime completedAt = now();
        PropertyFinancialTransaction effect = PropertyFinancialTransaction.record(
                UUID.randomUUID().toString(), original.getHotel(), original.getReservation(), original.getInvoiceId(),
                null, original, PropertyFinancialTransaction.TransactionType.REFUND,
                PropertyFinancialTransaction.Direction.CREDIT, refund.requestedMoney(), original.getMethod(),
                original.getProvider(), original.getEnvironment(), providerReference, effectIdentity,
                "PROVIDER", null, refund.getReason(), completedAt);
        transactionRepository.saveAndFlush(effect);
        String previous = refund.getStatus().name();
        refund.markSucceeded(refund.requestedMoney(), completedAt);
        requestRepository.saveAndFlush(refund);
        audit(refund, null, previous, refund.getStatus().name(), "Property refund succeeded",
                correlationId, Map.of("amount", refund.getRequestedAmount(), "transactionPublicId", effect.getPublicId()));
        return result(refund, refundableBalance(original), false);
    }

    @Transactional
    public RefundResult fail(String refundPublicId, String correlationId) {
        PropertyRefundRequest refund = lockedRequest(refundPublicId);
        String previous = refund.getStatus().name();
        refund.markFailed(now());
        requestRepository.saveAndFlush(refund);
        audit(refund, null, previous, refund.getStatus().name(), "Property refund failed",
                correlationId, Map.of("amount", refund.getRequestedAmount()));
        return result(refund, refundableBalance(refund.getOriginalTransaction()), false);
    }

    @Transactional(readOnly = true)
    public RefundResult get(String refundPublicId) {
        PropertyRefundRequest refund = requestRepository.findByPublicId(requireText(refundPublicId, "refundId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(refund.getOriginalTransaction());
        return result(refund, refundableBalance(refund.getOriginalTransaction()), false);
    }

    private PropertyRefundRequest lockedRequest(String refundPublicId) {
        return requestRepository.findByPublicIdForUpdate(requireText(refundPublicId, "refundId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
    }

    private VndMoney availableForNewRequest(PropertyFinancialTransaction original) {
        BigDecimal reserved = requestRepository.findByOriginalTransactionIdOrderByRequestedAtAsc(original.getId()).stream()
                .filter(request -> request.getStatus() == RefundState.REQUESTED
                        || request.getStatus() == RefundState.PENDING_APPROVAL
                        || request.getStatus() == RefundState.PENDING_PROVIDER)
                .map(PropertyRefundRequest::getRequestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = refundableBalance(original).amount().subtract(reserved);
        return VndMoney.of(available.max(BigDecimal.ZERO));
    }

    private VndMoney refundableBalance(PropertyFinancialTransaction original) {
        BigDecimal succeeded = transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(original.getId()).stream()
                .filter(transaction -> transaction.getTransactionType() == PropertyFinancialTransaction.TransactionType.REFUND
                        && transaction.getDirection() == PropertyFinancialTransaction.Direction.CREDIT)
                .map(PropertyFinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return VndMoney.of(original.getAmount().subtract(succeeded).max(BigDecimal.ZERO));
    }

    private void authorize(PropertyFinancialTransaction original) {
        User actor = propertyAccessService.currentUser();
        Long actorId = actor.getId();
        Long customerId = original.getReservation() == null || original.getReservation().getUser() == null
                ? null : original.getReservation().getUser().getId();
        boolean propertyRole = propertyAccessService.isSystemAdministrator()
                || propertyAccessService.accessibleHotelIds().contains(original.getHotel().getId());
        if (!Objects.equals(actorId, customerId) && !propertyRole) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void validateOriginal(PropertyFinancialTransaction original) {
        if (original.isLegacyReconciliationRequired()
                || original.getDirection() != PropertyFinancialTransaction.Direction.DEBIT
                || original.getTransactionType() == PropertyFinancialTransaction.TransactionType.REFUND) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private boolean isCancellationRefundable(PropertyFinancialTransaction transaction) {
        return transaction != null
                && !transaction.isLegacyReconciliationRequired()
                && transaction.getDirection() == PropertyFinancialTransaction.Direction.DEBIT
                && transaction.getTransactionType() != PropertyFinancialTransaction.TransactionType.REFUND
                && transaction.getTransactionType() != PropertyFinancialTransaction.TransactionType.MANUAL_ADJUSTMENT;
    }

    private void verifyReplay(PropertyRefundRequest existing, String requestHash) {
        if (!MessageDigest.isEqual(existing.getRequestHash().getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private void audit(
            PropertyRefundRequest refund,
            User actor,
            String previous,
            String next,
            String reason,
            String correlationId,
            Map<String, ?> metadata) {
        auditService.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE", refund.getHotel().getId(), "PROPERTY_REFUND_REQUEST", refund.getPublicId(),
                actor == null ? "PROVIDER" : "USER", actor == null ? null : actor.getId(), "REFUND",
                previous, next, reason, "PROPERTY-REFUND:" + refund.getPublicId(), null, correlationId, metadata));
    }

    private RefundResult result(PropertyRefundRequest refund, VndMoney remaining, boolean replayed) {
        return new RefundResult(refund.getPublicId(), refund.getOriginalTransaction().getPublicId(),
                refund.getRequestedAmount(), refund.getCurrency(), refund.getStatus(), remaining.amount(),
                refund.getRequestedAt(), refund.getCompletedAt(),
                refund.getOriginalTransaction().getProvider(),
                refund.getOriginalTransaction().getEnvironment(),
                replayed);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void validate(RequestCommand command) {
        if (command == null) throw new IllegalArgumentException("Refund command is required.");
        requireText(command.transactionPublicId(), "transactionId");
        Objects.requireNonNull(command.amount(), "amount must not be null");
    }

    private String normalize(String value, String field, int maxLength) {
        String normalized = requireText(value, field);
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long.");
        return normalized;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }

    public record RequestCommand(
            String transactionPublicId,
            BigDecimal amount,
            String reason,
            String idempotencyKey,
            String correlationId) {
    }

    public record RefundResult(
            String publicId,
            String originalTransactionPublicId,
            BigDecimal requestedAmount,
            String currency,
            RefundState status,
            BigDecimal remainingRefundableAmount,
            LocalDateTime requestedAt,
            LocalDateTime completedAt,
            String provider,
            com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment environment,
            boolean replayed) {
    }
}

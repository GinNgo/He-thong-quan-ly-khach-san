package com.hotel.propertycommerce.payment;

import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PaymentReceiptEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ManualTransferConfirmationService {

    @Autowired(required = false)
    private PaymentReceiptEmailService paymentReceiptEmailService;

    private static final Set<String> MANUAL_METHODS = Set.of("MANUAL_TRANSFER", "QR_TRANSFER");

    private final PropertyPaymentAttemptRepository attemptRepository;
    private final PropertyFinancialTransactionRepository transactionRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialIdempotencyService idempotencyService;
    private final FinancialAuditService auditService;
    private final Clock clock;

    @Autowired
    public ManualTransferConfirmationService(
            PropertyPaymentAttemptRepository attemptRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyAccessService propertyAccessService,
            FinancialIdempotencyService idempotencyService,
            FinancialAuditService auditService) {
        this(attemptRepository, transactionRepository, propertyAccessService,
                idempotencyService, auditService, Clock.systemUTC());
    }

    ManualTransferConfirmationService(
            PropertyPaymentAttemptRepository attemptRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyAccessService propertyAccessService,
            FinancialIdempotencyService idempotencyService,
            FinancialAuditService auditService,
            Clock clock) {
        this.attemptRepository = attemptRepository;
        this.transactionRepository = transactionRepository;
        this.propertyAccessService = propertyAccessService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public ConfirmationResult confirm(ConfirmCommand command) {
        validate(command);
        requireApprovalPermission();
        User actor = propertyAccessService.currentUser();
        PropertyPaymentAttempt attempt = attemptRepository.findByPublicIdForUpdate(command.attemptPublicId().trim())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(attempt, actor);
        validateManualAttempt(attempt);

        ConfirmationIdentity payload = new ConfirmationIdentity(
                attempt.getPublicId(), command.reason().trim(), command.evidenceReference().trim());
        FinancialIdempotencyService.BeginResult begin = idempotencyService.begin(
                new FinancialIdempotencyService.BeginCommand(
                        "PROPERTY_COMMERCE",
                        "CONFIRM_MANUAL_PAYMENT",
                        "HOTEL:" + attempt.getHotel().getId(),
                        command.idempotencyKey().trim(),
                        payload,
                        attempt.getHotel().getId(),
                        actor.getId(),
                        command.correlationId()));

        String ledgerIdentity = ledgerIdentity(attempt.getPublicId());
        if (begin instanceof FinancialIdempotencyService.Replay replay) {
            return response(findTransaction(replay.responseBody()), true);
        }
        if (begin instanceof FinancialIdempotencyService.InProgress) {
            return transactionRepository.findByIdempotencyIdentity(ledgerIdentity)
                    .map(transaction -> response(transaction, true))
                    .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        }
        if (begin instanceof FinancialIdempotencyService.RetryableFailure) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
        }

        FinancialIdempotencyService.Acquired acquired = (FinancialIdempotencyService.Acquired) begin;
        PropertyFinancialTransaction existing = transactionRepository.findByIdempotencyIdentity(ledgerIdentity)
                .orElseGet(() -> transactionRepository.findByAttemptIdOrderByOccurredAtAsc(attempt.getId()).stream()
                        .filter(transaction -> transaction.getDirection()
                                == PropertyFinancialTransaction.Direction.DEBIT)
                        .findFirst()
                        .orElse(null));
        if (existing != null && (!sameAttempt(existing.getAttempt(), attempt)
                || !sameText(existing.getProviderTransactionReference(), command.evidenceReference().trim()))) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        if (attempt.getStatus() == PaymentState.SUCCESS) {
            if (existing == null) {
                throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
            }
            idempotencyService.complete(acquired.recordId(), 200, existing.getPublicId());
            audit(attempt, actor, command, PaymentState.SUCCESS, PaymentState.SUCCESS,
                    "Equivalent manual confirmation replay", true, existing.getIdempotencyIdentity());
            return response(existing, true);
        }
        if (attempt.getStatus() != PaymentState.PENDING_VERIFICATION) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    FinancialErrorCode.INVALID_STATE_TRANSITION.defaultMessage(),
                    null,
                    attempt.getStatus().name(),
                    null);
        }

        Instant confirmedInstant = clock.instant();
        LocalDateTime confirmedAt = LocalDateTime.ofInstant(confirmedInstant, ZoneOffset.UTC);
        bindEvidence(attempt, command, ledgerIdentity);
        attempt.transitionTo(PaymentState.SUCCESS, confirmedAt, actor, null);
        attemptRepository.saveAndFlush(attempt);

        PropertyFinancialTransaction transaction = existing;
        if (transaction == null) {
            transaction = PropertyFinancialTransaction.record(
                    UUID.randomUUID().toString(),
                    attempt.getHotel(),
                    attempt.getReservation(),
                    null,
                    attempt,
                    null,
                    transactionType(attempt.getPurpose()),
                    PropertyFinancialTransaction.Direction.DEBIT,
                    VndMoney.of(attempt.getExpectedAmount()),
                    attempt.getMethod(),
                    attempt.getProvider(),
                    attempt.getEnvironment(),
                    command.evidenceReference().trim(),
                    ledgerIdentity,
                    "USER",
                    actor.getId(),
                    command.reason().trim(),
                    confirmedAt);
            transaction = transactionRepository.saveAndFlush(transaction);
        }

        idempotencyService.complete(acquired.recordId(), 200, transaction.getPublicId());
        audit(attempt, actor, command, PaymentState.PENDING_VERIFICATION, PaymentState.SUCCESS,
                command.reason().trim(), false, ledgerIdentity);
        if (paymentReceiptEmailService != null) {
            paymentReceiptEmailService.sendPropertyReceiptAfterCommit(attempt, transaction);
        }
        return response(transaction, false);
    }

    private void requireApprovalPermission() {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
        Integer mask = userDetails.getPermissionMasks() == null
                ? null
                : userDetails.getPermissionMasks().get(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL);
        if (mask == null || (mask & ActionCode.APPROVE) != ActionCode.APPROVE) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
    }

    private void authorize(PropertyPaymentAttempt attempt, User actor) {
        if (attempt.getReservation().getUser() != null
                && attempt.getReservation().getUser().getId() != null
                && attempt.getReservation().getUser().getId().equals(actor.getId())) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED,
                    "Customers cannot confirm their own manual payment.");
        }
        if (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(attempt.getHotel().getId())) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void validateManualAttempt(PropertyPaymentAttempt attempt) {
        if (!MANUAL_METHODS.contains(attempt.getMethod())) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Only manual or QR transfer attempts can be confirmed manually.");
        }
        if (attempt.getStatus() != PaymentState.SUCCESS
                && !attempt.getExpiresAt().toInstant(ZoneOffset.UTC).isAfter(clock.instant())) {
            throw new FinancialException(FinancialErrorCode.ATTEMPT_EXPIRED);
        }
    }

    private void bindEvidence(
            PropertyPaymentAttempt attempt,
            ConfirmCommand command,
            String ledgerIdentity) {
        try {
            if (attempt.getProviderOrderReference() == null) {
                attempt.bindProviderOrderReference(attempt.getPublicId());
            }
            attempt.bindProviderTransactionReference(command.evidenceReference().trim());
            attempt.bindProviderEventId("MANUAL:" + sha256(ledgerIdentity + "|" + command.evidenceReference().trim()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
    }

    private PropertyFinancialTransaction.TransactionType transactionType(PropertyPaymentAttempt.Purpose purpose) {
        return switch (purpose) {
            case DEPOSIT -> PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT;
            case BALANCE -> PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT;
            case SERVICE -> PropertyFinancialTransaction.TransactionType.SERVICE_PAYMENT;
            case SURCHARGE -> PropertyFinancialTransaction.TransactionType.SURCHARGE;
            case OTHER -> PropertyFinancialTransaction.TransactionType.MANUAL_ADJUSTMENT;
        };
    }

    private PropertyFinancialTransaction findTransaction(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
        }
        return transactionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
    }

    private ConfirmationResult response(PropertyFinancialTransaction transaction, boolean replayed) {
        PropertyPaymentAttempt attempt = transaction.getAttempt();
        return new ConfirmationResult(
                attempt.getPublicId(),
                transaction.getPublicId(),
                attempt.getStatus(),
                transaction.getAmount(),
                transaction.getOccurredAt(),
                replayed);
    }

    private void audit(
            PropertyPaymentAttempt attempt,
            User actor,
            ConfirmCommand command,
            PaymentState previousState,
            PaymentState newState,
            String reason,
            boolean replayed,
            String idempotencyIdentity) {
        auditService.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE",
                attempt.getHotel().getId(),
                "PROPERTY_PAYMENT_ATTEMPT",
                attempt.getPublicId(),
                "USER",
                actor.getId(),
                "MANUAL_CONFIRMATION",
                previousState.name(),
                newState.name(),
                reason,
                idempotencyIdentity,
                attempt.getProviderEventId(),
                command.correlationId(),
                Map.of(
                        "method", attempt.getMethod(),
                        "amount", attempt.getExpectedAmount(),
                        "evidenceReference", command.evidenceReference().trim(),
                        "replayed", replayed)));
    }

    private String ledgerIdentity(String attemptPublicId) {
        return "PROP-MANUAL:" + sha256(attemptPublicId);
    }

    private boolean sameAttempt(PropertyPaymentAttempt left, PropertyPaymentAttempt right) {
        if (left == null || right == null) return false;
        if (left.getId() != null && right.getId() != null) return left.getId().equals(right.getId());
        return left == right;
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void validate(ConfirmCommand command) {
        if (command == null
                || command.attemptPublicId() == null || command.attemptPublicId().isBlank()
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.reason() == null || command.reason().isBlank()
                || command.evidenceReference() == null || command.evidenceReference().isBlank()) {
            throw new IllegalArgumentException("Attempt, idempotency key, reason and evidence are required.");
        }
        if (command.reason().trim().length() > 1000) {
            throw new IllegalArgumentException("Manual confirmation reason is too long.");
        }
        if (command.evidenceReference().trim().length() > 200) {
            throw new IllegalArgumentException("Manual confirmation evidence reference is too long.");
        }
    }

    private record ConfirmationIdentity(String attemptPublicId, String reason, String evidenceReference) {
    }

    public record ConfirmCommand(
            String attemptPublicId,
            String reason,
            String evidenceReference,
            String idempotencyKey,
            String correlationId) {
    }

    public record ConfirmationResult(
            String attemptPublicId,
            String transactionPublicId,
            PaymentState status,
            java.math.BigDecimal amount,
            LocalDateTime confirmedAt,
            boolean replayed) {
    }
}

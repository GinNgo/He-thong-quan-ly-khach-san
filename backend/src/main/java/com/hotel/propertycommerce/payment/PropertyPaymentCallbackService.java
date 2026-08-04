package com.hotel.propertycommerce.payment;

import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.FinancialTransitionPolicy;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import com.hotel.services.PaymentReceiptEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PropertyPaymentCallbackService {

    @Autowired(required = false)
    private PaymentReceiptEmailService paymentReceiptEmailService;

    private final PropertyPaymentAttemptRepository attemptRepository;
    private final PropertyFinancialTransactionRepository transactionRepository;
    private final PaymentProviderAdapterRegistry adapterRegistry;
    private final FinancialAuditService auditService;
    private final Clock clock;

    @Autowired
    public PropertyPaymentCallbackService(
            PropertyPaymentAttemptRepository attemptRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PaymentProviderAdapterRegistry adapterRegistry,
            FinancialAuditService auditService) {
        this(attemptRepository, transactionRepository, adapterRegistry, auditService, Clock.systemUTC());
    }

    PropertyPaymentCallbackService(
            PropertyPaymentAttemptRepository attemptRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PaymentProviderAdapterRegistry adapterRegistry,
            FinancialAuditService auditService,
            Clock clock) {
        this.attemptRepository = attemptRepository;
        this.transactionRepository = transactionRepository;
        this.adapterRegistry = adapterRegistry;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public CallbackResult process(CallbackCommand command) {
        validate(command);
        String provider = normalizeCode(command.provider());
        PaymentProviderAdapter adapter = adapterRegistry.require(provider);
        Instant receivedAt = command.receivedAt() == null ? clock.instant() : command.receivedAt();
        PaymentProviderAdapter.NormalizedCallback normalized;
        try {
            normalized = adapter.normalize(new PaymentProviderAdapter.VerificationRequest(
                    null, command.expectedMerchantId(), null, null, null, null,
                    null, null, command.signature(), command.payload(), command.credentials(),
                    null, receivedAt));
        } catch (RuntimeException exception) {
            auditUnknown(command, provider, null, FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
            return CallbackResult.rejected(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID, null);
        }
        if (normalized.reference() == null || normalized.reference().isBlank()) {
            auditUnknown(command, provider, normalized, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
            return CallbackResult.rejected(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH, null);
        }

        PropertyPaymentAttempt attempt = attemptRepository.findByProviderAndReferenceForUpdate(
                        provider, normalized.reference().trim())
                .orElse(null);
        if (attempt == null) {
            auditUnknown(command, provider, normalized, FinancialErrorCode.RESOURCE_NOT_FOUND);
            return CallbackResult.rejected(FinancialErrorCode.RESOURCE_NOT_FOUND, null);
        }
        if (attempt.getEnvironment() != command.environment()) {
            return reject(attempt, command, normalized, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }

        String expectedReference = attempt.getProviderOrderReference() == null
                ? attempt.getPublicId()
                : attempt.getProviderOrderReference();
        PaymentProviderAdapter.VerificationRequest verificationRequest = new PaymentProviderAdapter.VerificationRequest(
                null,
                requireText(command.expectedMerchantId()),
                attempt.getExpectedAmount(),
                normalized.amount(),
                attempt.getCurrency(),
                normalized.currency(),
                expectedReference,
                normalized.reference(),
                command.signature(),
                command.payload(),
                command.credentials(),
                attempt.getExpiresAt().toInstant(ZoneOffset.UTC),
                receivedAt);
        PaymentProviderAdapter.VerificationResult verification = adapter.verify(verificationRequest);
        if (!verification.accepted()) {
            return reject(attempt, command, normalized, verification.errorCode());
        }

        PropertyPaymentAttempt eventOwner = attemptRepository.findByProviderEventForUpdate(
                        provider, attempt.getEnvironment(), requireText(normalized.eventId()))
                .orElse(null);
        if (eventOwner != null && !sameAttempt(eventOwner, attempt)) {
            return reject(attempt, command, normalized, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }

        PropertyFinancialTransaction existing = successfulTransaction(attempt);
        if (attempt.getStatus() == PaymentState.SUCCESS) {
            if (existing != null && sameText(existing.getProviderTransactionReference(), normalized.transactionId())) {
                audit(attempt, command, normalized, PaymentState.SUCCESS, PaymentState.SUCCESS,
                        "Equivalent provider callback replay", true, null);
                return CallbackResult.accepted(attempt, existing, true);
            }
            return reject(attempt, command, normalized, FinancialErrorCode.INVALID_STATE_TRANSITION);
        }

        boolean successful = Boolean.TRUE.equals(normalized.metadata().get("successful"));
        PaymentState previousState = attempt.getStatus();
        PaymentState targetState = successful ? PaymentState.SUCCESS : PaymentState.FAILED;
        FinancialTransitionPolicy.Decision decision = FinancialTransitionPolicy.payment(previousState, targetState);
        if (decision == FinancialTransitionPolicy.Decision.REJECT) {
            return reject(attempt, command, normalized, FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
        if (decision == FinancialTransitionPolicy.Decision.IDEMPOTENT) {
            if (sameProviderEvidence(attempt, normalized)) {
                audit(attempt, command, normalized, previousState, targetState,
                        "Equivalent provider callback replay", true, null);
                return CallbackResult.accepted(attempt, existing, true);
            }
            return reject(attempt, command, normalized, FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
        if (!compatibleProviderEvidence(attempt, normalized)) {
            return reject(attempt, command, normalized, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        bindProviderEvidence(attempt, normalized);
        if (!successful) {
            String failureCode = failureCode(provider, normalized.metadata());
            attempt.transitionTo(PaymentState.FAILED, eventTime(normalized, receivedAt), null, failureCode);
            attemptRepository.saveAndFlush(attempt);
            audit(attempt, command, normalized, previousState, PaymentState.FAILED,
                    "Verified provider failure callback", false, failureCode);
            return CallbackResult.accepted(attempt, null, false);
        }

        if (existing != null) {
            if (sameText(existing.getProviderTransactionReference(), normalized.transactionId())) {
                attempt.transitionTo(PaymentState.SUCCESS, eventTime(normalized, receivedAt), null, null);
                attemptRepository.saveAndFlush(attempt);
                audit(attempt, command, normalized, previousState, PaymentState.SUCCESS,
                        "Equivalent provider ledger replay", true, null);
                return CallbackResult.accepted(attempt, existing, true);
            }
            return reject(attempt, command, normalized, FinancialErrorCode.CONCURRENT_MODIFICATION);
        }

        attempt.transitionTo(PaymentState.SUCCESS, eventTime(normalized, receivedAt), null, null);
        attemptRepository.saveAndFlush(attempt);

        String idempotencyIdentity = callbackIdentity(provider, attempt.getEnvironment(), normalized.eventId());
        PropertyFinancialTransaction transaction = transactionRepository.findByIdempotencyIdentity(idempotencyIdentity)
                .orElse(null);
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
                    provider,
                    attempt.getEnvironment(),
                    normalized.transactionId(),
                    idempotencyIdentity,
                    "PROVIDER",
                    null,
                    "Verified provider callback",
                    eventTime(normalized, receivedAt));
            try {
                transaction = transactionRepository.saveAndFlush(transaction);
            } catch (DataIntegrityViolationException exception) {
                throw new FinancialException(
                        FinancialErrorCode.CONCURRENT_MODIFICATION,
                        FinancialErrorCode.CONCURRENT_MODIFICATION.defaultMessage(),
                        null,
                        attempt.getStatus().name(),
                        exception);
            }
        } else if (!sameAttempt(transaction.getAttempt(), attempt)) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
        }

        audit(attempt, command, normalized, previousState, PaymentState.SUCCESS,
                "Verified provider payment", false, null);
        if (paymentReceiptEmailService != null) {
            paymentReceiptEmailService.sendPropertyReceiptAfterCommit(attempt, transaction);
        }
        return CallbackResult.accepted(attempt, transaction, false);
    }

    private CallbackResult reject(
            PropertyPaymentAttempt attempt,
            CallbackCommand command,
            PaymentProviderAdapter.NormalizedCallback callback,
            FinancialErrorCode errorCode) {
        audit(attempt, command, callback, attempt.getStatus(), attempt.getStatus(),
                errorCode.defaultMessage(), false, errorCode.name());
        return CallbackResult.rejected(errorCode, attempt.getPublicId(), attempt.getStatus());
    }

    private void bindProviderEvidence(
            PropertyPaymentAttempt attempt,
            PaymentProviderAdapter.NormalizedCallback callback) {
        try {
            attempt.bindProviderOrderReference(callback.reference());
            if (callback.transactionId() != null && !callback.transactionId().isBlank()) {
                attempt.bindProviderTransactionReference(callback.transactionId());
            }
            attempt.bindProviderEventId(callback.eventId());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
    }

    private boolean compatibleProviderEvidence(
            PropertyPaymentAttempt attempt,
            PaymentProviderAdapter.NormalizedCallback callback) {
        return compatible(attempt.getProviderOrderReference(), callback.reference())
                && compatible(attempt.getProviderTransactionReference(), callback.transactionId())
                && compatible(attempt.getProviderEventId(), callback.eventId());
    }

    private boolean sameProviderEvidence(
            PropertyPaymentAttempt attempt,
            PaymentProviderAdapter.NormalizedCallback callback) {
        return sameText(attempt.getProviderOrderReference(), callback.reference())
                && sameText(attempt.getProviderEventId(), callback.eventId())
                && (attempt.getProviderTransactionReference() == null
                && (callback.transactionId() == null || callback.transactionId().isBlank())
                || sameText(attempt.getProviderTransactionReference(), callback.transactionId()));
    }

    private boolean compatible(String current, String incoming) {
        if (current == null) return true;
        return sameText(current, incoming);
    }

    private PropertyFinancialTransaction successfulTransaction(PropertyPaymentAttempt attempt) {
        return transactionRepository.findByAttemptIdOrderByOccurredAtAsc(attempt.getId()).stream()
                .filter(transaction -> transaction.getDirection() == PropertyFinancialTransaction.Direction.DEBIT)
                .findFirst()
                .orElse(null);
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

    private LocalDateTime eventTime(PaymentProviderAdapter.NormalizedCallback callback, Instant receivedAt) {
        Instant occurredAt = callback.occurredAt() == null ? receivedAt : callback.occurredAt();
        return LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
    }

    private String failureCode(String provider, Map<String, ?> metadata) {
        Object value = metadata.get("resultCode");
        if (value == null) value = metadata.get("responseCode");
        if (value == null) value = metadata.get("status");
        String code = provider + "_" + (value == null ? "FAILED" : value.toString());
        return code.length() <= 100 ? code : code.substring(0, 100);
    }

    private String callbackIdentity(String provider, PaymentEnvironment environment, String eventId) {
        return "PROP-CB:" + sha256(provider + "|" + environment + "|" + eventId);
    }

    private void audit(
            PropertyPaymentAttempt attempt,
            CallbackCommand command,
            PaymentProviderAdapter.NormalizedCallback callback,
            PaymentState previousState,
            PaymentState newState,
            String reason,
            boolean replayed,
            String errorCode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", attempt.getProvider());
        metadata.put("environment", attempt.getEnvironment().name());
        metadata.put("amount", callback == null ? null : callback.amount());
        metadata.put("currency", callback == null ? null : callback.currency());
        metadata.put("replayed", replayed);
        metadata.put("errorCode", errorCode);
        auditService.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE",
                attempt.getHotel().getId(),
                "PROPERTY_PAYMENT_ATTEMPT",
                attempt.getPublicId(),
                "PROVIDER",
                null,
                attempt.getProvider(),
                previousState == null ? null : previousState.name(),
                newState == null ? null : newState.name(),
                reason,
                callback == null ? null : callbackIdentity(
                        attempt.getProvider(), attempt.getEnvironment(), callback.eventId()),
                callback == null ? null : callback.eventId(),
                command.correlationId(),
                metadata));
    }

    private void auditUnknown(
            CallbackCommand command,
            String provider,
            PaymentProviderAdapter.NormalizedCallback callback,
            FinancialErrorCode errorCode) {
        String reference = callback == null ? null : callback.reference();
        auditService.append(new FinancialAuditService.AuditCommand(
                "PAYMENT_PROVIDER",
                null,
                "PROPERTY_PAYMENT_CALLBACK",
                "CALLBACK:" + sha256(provider + "|" + (reference == null ? "UNKNOWN" : reference)),
                "PROVIDER",
                null,
                provider,
                null,
                null,
                errorCode.defaultMessage(),
                null,
                callback == null ? null : callback.eventId(),
                command.correlationId(),
                Map.of("provider", provider, "errorCode", errorCode.name())));
    }

    private void validate(CallbackCommand command) {
        if (command == null || command.environment() == null) {
            throw new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED);
        }
        if (command.provider() == null || command.provider().isBlank()
                || command.expectedMerchantId() == null || command.expectedMerchantId().isBlank()) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (command.payload() == null || command.payload().isEmpty()) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        if (command.credentials() == null || command.credentials().isEmpty()) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    private boolean sameAttempt(PropertyPaymentAttempt left, PropertyPaymentAttempt right) {
        if (left == null || right == null) return false;
        if (left.getId() != null && right.getId() != null) return left.getId().equals(right.getId());
        return left == right;
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private String normalizeCode(String value) {
        return requireText(value).toUpperCase(Locale.ROOT);
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record CallbackCommand(
            String provider,
            PaymentEnvironment environment,
            String expectedMerchantId,
            String signature,
            Map<String, ?> payload,
            Map<String, ?> credentials,
            Instant receivedAt,
            String correlationId) {
    }

    public record CallbackResult(
            boolean accepted,
            boolean replayed,
            FinancialErrorCode errorCode,
            String attemptPublicId,
            PaymentState status,
            String transactionPublicId) {

        static CallbackResult accepted(
                PropertyPaymentAttempt attempt,
                PropertyFinancialTransaction transaction,
                boolean replayed) {
            return new CallbackResult(true, replayed, null, attempt.getPublicId(), attempt.getStatus(),
                    transaction == null ? null : transaction.getPublicId());
        }

        static CallbackResult rejected(FinancialErrorCode errorCode, String reference) {
            return new CallbackResult(false, false, errorCode, reference, null, null);
        }

        static CallbackResult rejected(
                FinancialErrorCode errorCode,
                String reference,
                PaymentState status) {
            return new CallbackResult(false, false, errorCode, reference, status, null);
        }
    }
}

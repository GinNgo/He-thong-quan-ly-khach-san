package com.hotel.platformbilling.payment;

import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.subscription.SubscriptionApplicationService;
import com.hotel.platformbilling.subscription.SubscriptionRenewalService;
import com.hotel.platformbilling.subscription.SubscriptionUpgradeService;
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
public class PlatformPaymentCallbackService {

    @Autowired(required = false)
    private PaymentReceiptEmailService paymentReceiptEmailService;

    private final PlatformPaymentAttemptRepository attemptRepository;
    private final PlatformFinancialTransactionRepository transactionRepository;
    private final PlatformSubscriptionOrderRepository orderRepository;
    private final PlatformPaymentConfigurationService configurationService;
    private final PaymentProviderAdapterRegistry adapterRegistry;
    private final FinancialAuditService auditService;
    private final SubscriptionApplicationService applicationService;
    private final SubscriptionRenewalService renewalService;
    private final SubscriptionUpgradeService upgradeService;
    private final Clock clock;

    @Autowired
    public PlatformPaymentCallbackService(
            PlatformPaymentAttemptRepository attemptRepository,
            PlatformFinancialTransactionRepository transactionRepository,
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformPaymentConfigurationService configurationService,
            PaymentProviderAdapterRegistry adapterRegistry,
            FinancialAuditService auditService,
            SubscriptionApplicationService applicationService,
            SubscriptionRenewalService renewalService,
            SubscriptionUpgradeService upgradeService) {
        this(
                attemptRepository,
                transactionRepository,
                orderRepository,
                configurationService,
                adapterRegistry,
                auditService,
                applicationService,
                renewalService,
                upgradeService,
                Clock.systemUTC());
    }

    public PlatformPaymentCallbackService(
            PlatformPaymentAttemptRepository attemptRepository,
            PlatformFinancialTransactionRepository transactionRepository,
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformPaymentConfigurationService configurationService,
            PaymentProviderAdapterRegistry adapterRegistry,
            FinancialAuditService auditService,
            SubscriptionApplicationService applicationService,
            SubscriptionRenewalService renewalService,
            SubscriptionUpgradeService upgradeService,
            Clock clock) {
        this.attemptRepository = attemptRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.configurationService = configurationService;
        this.adapterRegistry = adapterRegistry;
        this.auditService = auditService;
        this.applicationService = applicationService;
        this.renewalService = renewalService;
        this.upgradeService = upgradeService;
        this.clock = clock;
    }

    @Transactional
    public CallbackResult process(CallbackCommand command) {
        validate(command);
        String provider = normalizeCode(command.provider());
        PaymentProviderAdapter adapter = adapterRegistry.require(provider);
        Instant receivedAt = command.receivedAt() == null ? clock.instant() : command.receivedAt();
        PaymentProviderAdapter.NormalizedCallback callback;
        try {
            callback = adapter.normalize(new PaymentProviderAdapter.VerificationRequest(
                    null, null, null, null, null, null,
                    null, null, command.signature(), command.payload(), Map.of(),
                    null, receivedAt));
        } catch (RuntimeException exception) {
            auditUnknown(command, provider, null, FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
            return CallbackResult.rejected(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID, null, null);
        }
        if (callback.reference() == null || callback.reference().isBlank()) {
            auditUnknown(command, provider, callback, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
            return CallbackResult.rejected(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH, null, null);
        }

        PlatformPaymentAttempt attempt = attemptRepository.findByProviderAndReferenceForUpdate(
                        provider, callback.reference().trim())
                .orElse(null);
        if (attempt == null) {
            auditUnknown(command, provider, callback, FinancialErrorCode.RESOURCE_NOT_FOUND);
            return CallbackResult.rejected(FinancialErrorCode.RESOURCE_NOT_FOUND, null, null);
        }
        SubscriptionOrder order = orderRepository.findByPublicIdForUpdate(attempt.getOrder().getPublicId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        PlatformPaymentConfigurationService.ReadyConfiguration ready = configurationService.requireReady(provider);
        if (!sameConfiguration(ready.configuration().getId(), attempt.getConfiguration().getId())
                || ready.configuration().getEnvironment() != attempt.getEnvironment()) {
            return reject(attempt, order, command, callback, FinancialErrorCode.CALLBACK_MERCHANT_MISMATCH);
        }
        if (ready.credentials() == null || ready.credentials().merchantId() == null) {
            return reject(attempt, order, command, callback, FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }

        String expectedReference = attempt.getProviderOrderReference() == null
                ? attempt.getPublicId()
                : attempt.getProviderOrderReference();
        PaymentProviderAdapter.VerificationRequest request = new PaymentProviderAdapter.VerificationRequest(
                null,
                ready.credentials().merchantId(),
                attempt.getExpectedAmount(),
                callback.amount(),
                attempt.getCurrency(),
                callback.currency(),
                expectedReference,
                callback.reference(),
                command.signature(),
                command.payload(),
                ready.credentials().secrets(),
                attempt.getExpiresAt().toInstant(ZoneOffset.UTC),
                receivedAt);
        PaymentProviderAdapter.VerificationResult verification = adapter.verify(request);
        if (!verification.accepted()) {
            return reject(attempt, order, command, callback, verification.errorCode());
        }
        if (callback.eventId() == null || callback.eventId().isBlank()) {
            return reject(attempt, order, command, callback, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }

        PlatformPaymentAttempt eventOwner = attemptRepository.findByProviderEventForUpdate(
                        provider, attempt.getEnvironment(), callback.eventId().trim())
                .orElse(null);
        if (eventOwner != null && !sameAttempt(eventOwner, attempt)) {
            return reject(attempt, order, command, callback, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }

        PlatformFinancialTransaction existing = successfulTransaction(attempt);
        if (attempt.getStatus() == PlatformPaymentAttempt.Status.SUCCESS) {
            if (existing != null && sameProviderEvidence(attempt, callback)) {
                audit(attempt, order, command, callback, "SUCCESS", "SUCCESS",
                        "Equivalent platform callback replay", true, null);
                String contractPublicId = applySubscriptionIfEligible(order, existing, command.correlationId());
                return CallbackResult.accepted(attempt, order, existing, contractPublicId, true);
            }
            return reject(attempt, order, command, callback, FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
        if (attempt.terminal()) {
            return reject(attempt, order, command, callback, FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
        if (!compatibleProviderEvidence(attempt, callback)) {
            return reject(attempt, order, command, callback, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }

        boolean successful = Boolean.TRUE.equals(callback.metadata().get("successful"));
        String previousAttemptState = attempt.getStatus().name();
        String previousOrderState = order.getStatus().name();
        LocalDateTime occurredAt = eventTime(callback, receivedAt);
        if (!successful) {
            String failureCode = failureCode(provider, callback.metadata());
            attempt.markFailed(failureCode, callback.eventId(), occurredAt);
            if (order.getStatus() == SubscriptionOrderState.PENDING_PAYMENT) {
                order.transitionTo(SubscriptionOrderState.FAILED, occurredAt);
                orderRepository.save(order);
            }
            attemptRepository.saveAndFlush(attempt);
            audit(attempt, order, command, callback, previousAttemptState, attempt.getStatus().name(),
                    "Verified platform payment failure", false, failureCode);
            return CallbackResult.accepted(attempt, order, null, null, false);
        }

        if (order.getStatus() != SubscriptionOrderState.PENDING_PAYMENT
                && order.getStatus() != SubscriptionOrderState.PAID) {
            return reject(attempt, order, command, callback, FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
        if (existing != null) {
            if (!sameProviderEvidence(existing, callback)) {
                return reject(attempt, order, command, callback, FinancialErrorCode.CONCURRENT_MODIFICATION);
            }
            attempt.markSucceeded(callback.transactionId(), callback.eventId(), occurredAt);
            if (order.getStatus() == SubscriptionOrderState.PENDING_PAYMENT) {
                order.transitionTo(SubscriptionOrderState.PAID, occurredAt);
                orderRepository.save(order);
            }
            attemptRepository.saveAndFlush(attempt);
            audit(attempt, order, command, callback, previousAttemptState, attempt.getStatus().name(),
                    "Repaired platform attempt from existing ledger effect", true, null);
            String contractPublicId = applySubscriptionIfEligible(order, existing, command.correlationId());
            return CallbackResult.accepted(attempt, order, existing, contractPublicId, true);
        }

        attempt.markSucceeded(callback.transactionId(), callback.eventId(), occurredAt);
        order.transitionTo(SubscriptionOrderState.PAID, occurredAt);
        attemptRepository.save(attempt);
        orderRepository.save(order);

        String identity = callbackIdentity(provider, attempt.getEnvironment().name(), callback.eventId());
        PlatformFinancialTransaction transaction = transactionRepository.findByIdempotencyIdentity(identity)
                .orElse(null);
        if (transaction == null) {
            transaction = PlatformFinancialTransaction.record(
                    UUID.randomUUID().toString(),
                    order,
                    attempt,
                    null,
                    transactionType(order.getOperation()),
                    PlatformFinancialTransaction.Direction.DEBIT,
                    VndMoney.of(attempt.getExpectedAmount()),
                    attempt.getMethod(),
                    provider,
                    attempt.getEnvironment(),
                    callback.transactionId(),
                    identity,
                    "PROVIDER",
                    null,
                    "Verified platform payment callback",
                    occurredAt);
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

        audit(attempt, order, command, callback, previousAttemptState, attempt.getStatus().name(),
                "Verified platform payment", false, null);
        auditOrder(order, command, callback, previousOrderState, order.getStatus().name(), identity);
        String contractPublicId = applySubscriptionIfEligible(order, transaction, command.correlationId());
        if (paymentReceiptEmailService != null) {
            paymentReceiptEmailService.sendPlatformReceiptAfterCommit(attempt, transaction);
        }
        return CallbackResult.accepted(attempt, order, transaction, contractPublicId, false);
    }

    private String applySubscriptionIfEligible(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            String correlationId) {
        return switch (order.getOperation()) {
            case PURCHASE -> applicationService.applyPaidOrder(
                    order.getPublicId(), transaction.getPublicId(), correlationId).contractPublicId();
            case RENEW -> renewalService.applyPaidRenewal(
                    order.getPublicId(), transaction.getPublicId(), correlationId).contractPublicId();
            case UPGRADE -> upgradeService.applyPaidUpgrade(
                    order.getPublicId(), transaction.getPublicId(), correlationId).contractPublicId();
            case DOWNGRADE, REFUND -> null;
        };
    }

    private PlatformFinancialTransaction successfulTransaction(PlatformPaymentAttempt attempt) {
        return transactionRepository.findByAttemptIdOrderByOccurredAtAsc(attempt.getId()).stream()
                .filter(transaction -> transaction.getDirection() == PlatformFinancialTransaction.Direction.DEBIT)
                .findFirst()
                .orElse(null);
    }

    private PlatformFinancialTransaction.TransactionType transactionType(SubscriptionOrder.Operation operation) {
        return switch (operation) {
            case PURCHASE -> PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE;
            case RENEW -> PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_RENEWAL;
            case UPGRADE -> PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_UPGRADE;
            case DOWNGRADE, REFUND -> throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED);
        };
    }

    private CallbackResult reject(
            PlatformPaymentAttempt attempt,
            SubscriptionOrder order,
            CallbackCommand command,
            PaymentProviderAdapter.NormalizedCallback callback,
            FinancialErrorCode errorCode) {
        audit(attempt, order, command, callback, attempt.getStatus().name(), attempt.getStatus().name(),
                errorCode.defaultMessage(), false, errorCode.name());
        return new CallbackResult(
                false,
                false,
                errorCode,
                attempt.getPublicId(),
                attempt.getStatus(),
                order.getStatus(),
                null,
                null);
    }

    private void audit(
            PlatformPaymentAttempt attempt,
            SubscriptionOrder order,
            CallbackCommand command,
            PaymentProviderAdapter.NormalizedCallback callback,
            String previousState,
            String newState,
            String reason,
            boolean replayed,
            String errorCode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", attempt.getProvider());
        metadata.put("environment", attempt.getEnvironment().name());
        metadata.put("orderPublicId", order.getPublicId());
        metadata.put("amount", callback == null ? null : callback.amount());
        metadata.put("currency", callback == null ? null : callback.currency());
        metadata.put("replayed", replayed);
        metadata.put("errorCode", errorCode);
        auditService.append(new FinancialAuditService.AuditCommand(
                "PLATFORM_BILLING",
                null,
                "PLATFORM_PAYMENT_ATTEMPT",
                attempt.getPublicId(),
                "PROVIDER",
                null,
                attempt.getProvider(),
                previousState,
                newState,
                reason,
                callback == null ? null : callbackIdentity(
                        attempt.getProvider(), attempt.getEnvironment().name(), callback.eventId()),
                callback == null ? null : callback.eventId(),
                command.correlationId(),
                metadata));
    }

    private void auditOrder(
            SubscriptionOrder order,
            CallbackCommand command,
            PaymentProviderAdapter.NormalizedCallback callback,
            String previousState,
            String newState,
            String identity) {
        auditService.append(new FinancialAuditService.AuditCommand(
                "PLATFORM_BILLING",
                null,
                "PLATFORM_SUBSCRIPTION_ORDER",
                order.getPublicId(),
                "PROVIDER",
                null,
                callback.provider(),
                previousState,
                newState,
                "Authoritative platform payment accepted",
                identity,
                callback.eventId(),
                command.correlationId(),
                Map.of("provider", callback.provider(), "operation", order.getOperation().name())));
    }

    private void auditUnknown(
            CallbackCommand command,
            String provider,
            PaymentProviderAdapter.NormalizedCallback callback,
            FinancialErrorCode errorCode) {
        String reference = callback == null ? "UNKNOWN" : callback.reference();
        auditService.append(new FinancialAuditService.AuditCommand(
                "PAYMENT_PROVIDER",
                null,
                "PLATFORM_PAYMENT_CALLBACK",
                "CALLBACK:" + sha256(provider + '|' + reference),
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

    private boolean compatibleProviderEvidence(
            PlatformPaymentAttempt attempt,
            PaymentProviderAdapter.NormalizedCallback callback) {
        return compatible(attempt.getProviderOrderReference(), callback.reference())
                && compatible(attempt.getProviderTransactionReference(), callback.transactionId())
                && compatible(attempt.getProviderEventId(), callback.eventId());
    }

    private boolean sameProviderEvidence(
            PlatformPaymentAttempt attempt,
            PaymentProviderAdapter.NormalizedCallback callback) {
        return sameText(attempt.getProviderOrderReference(), callback.reference())
                && sameText(attempt.getProviderTransactionReference(), callback.transactionId())
                && sameText(attempt.getProviderEventId(), callback.eventId());
    }

    private boolean sameProviderEvidence(
            PlatformFinancialTransaction transaction,
            PaymentProviderAdapter.NormalizedCallback callback) {
        return sameText(transaction.getProviderTransactionReference(), callback.transactionId());
    }

    private boolean compatible(String current, String incoming) {
        return current == null || sameText(current, incoming);
    }

    private boolean sameAttempt(PlatformPaymentAttempt left, PlatformPaymentAttempt right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private boolean sameConfiguration(Long left, Long right) {
        return left != null && right != null && left.equals(right);
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private LocalDateTime eventTime(PaymentProviderAdapter.NormalizedCallback callback, Instant receivedAt) {
        return LocalDateTime.ofInstant(
                callback.occurredAt() == null ? receivedAt : callback.occurredAt(),
                ZoneOffset.UTC);
    }

    private String failureCode(String provider, Map<String, ?> metadata) {
        Object value = metadata.get("resultCode");
        if (value == null) value = metadata.get("responseCode");
        if (value == null) value = metadata.get("status");
        String code = provider + '_' + (value == null ? "FAILED" : value.toString());
        return code.length() <= 100 ? code : code.substring(0, 100);
    }

    private String callbackIdentity(String provider, String environment, String eventId) {
        return "PLAT-CB:" + sha256(provider + '|' + environment + '|' + eventId);
    }

    private void validate(CallbackCommand command) {
        if (command == null || command.provider() == null || command.provider().isBlank()) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (command.payload() == null || command.payload().isEmpty()) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
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
            String signature,
            Map<String, ?> payload,
            Instant receivedAt,
            String correlationId) {
    }

    public record CallbackResult(
            boolean accepted,
            boolean replayed,
            FinancialErrorCode errorCode,
            String attemptPublicId,
            PlatformPaymentAttempt.Status attemptStatus,
            SubscriptionOrderState orderStatus,
            String transactionPublicId,
            String contractPublicId) {

        static CallbackResult accepted(
                PlatformPaymentAttempt attempt,
                SubscriptionOrder order,
                PlatformFinancialTransaction transaction,
                String contractPublicId,
                boolean replayed) {
            return new CallbackResult(
                    true,
                    replayed,
                    null,
                    attempt.getPublicId(),
                    attempt.getStatus(),
                    order.getStatus(),
                    transaction == null ? null : transaction.getPublicId(),
                    contractPublicId);
        }

        static CallbackResult rejected(
                FinancialErrorCode errorCode,
                String attemptPublicId,
                SubscriptionOrderState orderStatus) {
            return new CallbackResult(false, false, errorCode, attemptPublicId, null, orderStatus, null, null);
        }
    }
}

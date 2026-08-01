package com.hotel.platformbilling.refund;

import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PlatformRefundService {

    private final PlatformFinancialTransactionRepository transactionRepository;
    private final PlatformRefundRequestRepository requestRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;
    private final Map<String, PlatformRefundEntitlementPolicy> policies;
    private final String configuredPolicyVersion;
    private final Clock clock;

    public PlatformRefundService(
            PlatformFinancialTransactionRepository transactionRepository,
            PlatformRefundRequestRepository requestRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            List<PlatformRefundEntitlementPolicy> policies,
            @Value("${platform.billing.refund-policy-version:}") String configuredPolicyVersion) {
        this(transactionRepository, requestRepository, propertyAccessService, auditService, policies,
                configuredPolicyVersion, Clock.systemUTC());
    }

    PlatformRefundService(
            PlatformFinancialTransactionRepository transactionRepository,
            PlatformRefundRequestRepository requestRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            List<PlatformRefundEntitlementPolicy> policies,
            String configuredPolicyVersion,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.requestRepository = requestRepository;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
        this.policies = policies.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                policy -> normalizePolicy(policy.version()), policy -> policy));
        this.configuredPolicyVersion = normalizePolicy(configuredPolicyVersion);
        this.clock = clock;
    }

    @Transactional
    public RefundResult request(RequestCommand command) {
        validate(command);
        PlatformFinancialTransaction original = transactionRepository.findByPublicIdForUpdate(command.transactionPublicId().trim())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        User actor = authorize(original);
        validateOriginal(original);
        String idempotencyKey = normalize(command.idempotencyKey(), "idempotencyKey", 160);
        String reason = normalize(command.reason(), "reason", 1000);
        VndMoney requested = VndMoney.of(command.amount());
        if (requested.amount().signum() <= 0) throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT);
        String requestHash = hash(original.getPublicId() + '|' + requested.amount().toPlainString() + '|' + reason);
        PlatformRefundRequest existing = requestRepository.findByRequestedByIdAndIdempotencyKey(actor.getId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            verifyReplay(existing, requestHash);
            return result(existing, refundableBalance(original), true, policyAvailable(existing.getPolicyVersion()));
        }

        VndMoney available = availableForNewRequest(original);
        if (requested.amount().compareTo(available.amount()) > 0) {
            throw new FinancialException(FinancialErrorCode.REFUND_EXCEEDS_BALANCE);
        }
        String policySnapshot = configuredPolicyVersion == null ? "UNCONFIGURED" : configuredPolicyVersion;
        PlatformRefundRequest refund = PlatformRefundRequest.request(
                original, original.getOrder(), requested, reason, actor, policySnapshot,
                idempotencyKey, requestHash, now());
        boolean policyAvailable = policyAvailable(policySnapshot);
        if (!policyAvailable) refund.markPolicyBlocked();
        refund = requestRepository.saveAndFlush(refund);
        audit(refund, actor, null, refund.getStatus().name(),
                policyAvailable ? "Platform refund requested" : "Platform refund blocked: no approved entitlement policy",
                command.correlationId(), Map.of("amount", requested.amount(), "policyVersion", policySnapshot));
        return result(refund, policyAvailable ? available.subtract(requested) : available, false, policyAvailable);
    }

    @Transactional
    public RefundResult approve(String refundPublicId, String correlationId) {
        PlatformRefundRequest refund = lockedRequest(refundPublicId);
        User actor = authorize(refund.getOriginalTransaction());
        PlatformRefundEntitlementPolicy policy = requirePolicy(refund.getPolicyVersion());
        String previous = refund.getStatus().name();
        refund.approveForProvider(actor, normalizePolicy(policy.version()));
        requestRepository.saveAndFlush(refund);
        audit(refund, actor, previous, refund.getStatus().name(), "Platform refund approved for provider",
                correlationId, Map.of("policyVersion", policy.version(), "amount", refund.getRequestedAmount()));
        return result(refund, refundableBalance(refund.getOriginalTransaction()), false, true);
    }

    @Transactional
    public RefundResult completeSucceeded(String refundPublicId, String providerReference, String correlationId) {
        PlatformRefundRequest refund = lockedRequest(refundPublicId);
        PlatformRefundEntitlementPolicy policy = requirePolicy(refund.getPolicyVersion());
        PlatformFinancialTransaction original = transactionRepository.findByPublicIdForUpdate(
                        refund.getOriginalTransaction().getPublicId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        String identity = "PLATFORM-REFUND:" + refund.getPublicId();
        PlatformFinancialTransaction existing = transactionRepository.findByIdempotencyIdentity(identity).orElse(null);
        if (existing != null) {
            if (refund.getStatus() != RefundState.SUCCEEDED) {
                refund.markSucceeded(VndMoney.of(existing.getAmount()), existing.getOccurredAt());
                requestRepository.saveAndFlush(refund);
            }
            return result(refund, refundableBalance(original), true, true);
        }
        if (refund.getStatus() != RefundState.PENDING_PROVIDER) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
        VndMoney refundable = refundableBalance(original);
        if (refund.getRequestedAmount().compareTo(refundable.amount()) > 0) {
            throw new FinancialException(FinancialErrorCode.REFUND_EXCEEDS_BALANCE);
        }
        LocalDateTime completedAt = now();
        PlatformFinancialTransaction effect = PlatformFinancialTransaction.record(
                UUID.randomUUID().toString(), original.getOrder(), null, original,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_REFUND,
                PlatformFinancialTransaction.Direction.CREDIT, refund.requestedMoney(),
                original.getMethod(), original.getProvider(), original.getEnvironment(), providerReference,
                identity, "PROVIDER", null, refund.getReason(), completedAt);
        transactionRepository.saveAndFlush(effect);
        PlatformRefundEntitlementPolicy.PolicyEffect policyEffect = policy.apply(refund, effect, correlationId);
        String previous = refund.getStatus().name();
        refund.markSucceeded(refund.requestedMoney(), completedAt);
        requestRepository.saveAndFlush(refund);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyVersion", policy.version());
        metadata.put("transactionPublicId", effect.getPublicId());
        metadata.put("contractPublicId", policyEffect.contractPublicId());
        metadata.put("entitlementStatus", policyEffect.entitlementStatus());
        metadata.put("historyAction", policyEffect.historyAction());
        audit(refund, null, previous, refund.getStatus().name(), "Platform refund and entitlement policy applied",
                correlationId, metadata);
        return result(refund, refundableBalance(original), false, true);
    }

    @Transactional(readOnly = true)
    public RefundResult get(String refundPublicId) {
        PlatformRefundRequest refund = requestRepository.findByPublicId(requireText(refundPublicId, "refundId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(refund.getOriginalTransaction());
        return result(refund, refundableBalance(refund.getOriginalTransaction()), false,
                policyAvailable(refund.getPolicyVersion()));
    }

    private PlatformRefundRequest lockedRequest(String publicId) {
        return requestRepository.findByPublicIdForUpdate(requireText(publicId, "refundId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
    }

    private VndMoney availableForNewRequest(PlatformFinancialTransaction original) {
        BigDecimal reserved = requestRepository.findByOriginalTransactionOrderByRequestedAtAsc(original).stream()
                .filter(request -> request.getStatus() == RefundState.REQUESTED
                        || request.getStatus() == RefundState.PENDING_APPROVAL
                        || request.getStatus() == RefundState.PENDING_PROVIDER)
                .map(PlatformRefundRequest::getRequestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return VndMoney.of(refundableBalance(original).amount().subtract(reserved).max(BigDecimal.ZERO));
    }

    private VndMoney refundableBalance(PlatformFinancialTransaction original) {
        BigDecimal succeeded = transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(original.getId()).stream()
                .filter(transaction -> transaction.getTransactionType() == PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_REFUND
                        && transaction.getDirection() == PlatformFinancialTransaction.Direction.CREDIT)
                .map(PlatformFinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return VndMoney.of(original.getAmount().subtract(succeeded).max(BigDecimal.ZERO));
    }

    private User authorize(PlatformFinancialTransaction original) {
        User actor = propertyAccessService.currentUser();
        Long ownerId = original.getOrder().getOwner().getId();
        if (!propertyAccessService.isSystemAdministrator() && !Objects.equals(actor.getId(), ownerId)) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        return actor;
    }

    private void validateOriginal(PlatformFinancialTransaction original) {
        if (original.getDirection() != PlatformFinancialTransaction.Direction.DEBIT
                || original.getTransactionType() == PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_REFUND
                || original.getTransactionType() == PlatformFinancialTransaction.TransactionType.DOWNGRADE_CREDIT) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private PlatformRefundEntitlementPolicy requirePolicy(String version) {
        PlatformRefundEntitlementPolicy policy = version == null ? null : policies.get(normalizePolicy(version));
        if (policy == null) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "No approved platform refund entitlement policy is registered.");
        }
        return policy;
    }

    private boolean policyAvailable(String version) {
        return version != null && policies.containsKey(normalizePolicy(version));
    }

    private void verifyReplay(PlatformRefundRequest existing, String requestHash) {
        if (!MessageDigest.isEqual(existing.getRequestHash().getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private void audit(
            PlatformRefundRequest refund,
            User actor,
            String previous,
            String next,
            String reason,
            String correlationId,
            Map<String, ?> metadata) {
        auditService.append(new FinancialAuditService.AuditCommand(
                "PLATFORM_BILLING", null, "PLATFORM_REFUND_REQUEST", refund.getPublicId(),
                actor == null ? "PROVIDER" : "USER", actor == null ? null : actor.getId(), "REFUND",
                previous, next, reason, "PLATFORM-REFUND:" + refund.getPublicId(), null, correlationId, metadata));
    }

    private RefundResult result(
            PlatformRefundRequest refund,
            VndMoney remaining,
            boolean replayed,
            boolean policyAvailable) {
        return new RefundResult(refund.getPublicId(), refund.getOriginalTransaction().getPublicId(),
                refund.getOrder().getPublicId(), refund.getRequestedAmount(), refund.getCurrency(), refund.getStatus(),
                refund.getPolicyVersion(), policyAvailable, remaining.amount(), refund.getRequestedAt(),
                refund.getCompletedAt(), replayed);
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
        if (command == null) throw new IllegalArgumentException("Platform refund command is required.");
        requireText(command.transactionPublicId(), "transactionId");
        Objects.requireNonNull(command.amount(), "amount must not be null");
    }

    private String normalize(String value, String field, int maxLength) {
        String normalized = requireText(value, field);
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long.");
        return normalized;
    }

    private String normalizePolicy(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
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
            String orderPublicId,
            BigDecimal requestedAmount,
            String currency,
            RefundState status,
            String policyVersion,
            boolean policyAvailable,
            BigDecimal remainingRefundableAmount,
            LocalDateTime requestedAt,
            LocalDateTime completedAt,
            boolean replayed) {
    }
}

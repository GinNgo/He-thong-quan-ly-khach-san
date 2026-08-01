package com.hotel.platformbilling.refund;

import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "platform_refund_requests", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_platform_refund_public", columnNames = "public_id"),
        @UniqueConstraint(name = "UQ_platform_refund_idempotency", columnNames = {"requested_by", "idempotency_key"})
})
public class PlatformRefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_transaction_id", nullable = false, updatable = false)
    private PlatformFinancialTransaction originalTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private SubscriptionOrder order;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal requestedAmount;

    @Column(name = "succeeded_amount", precision = 19, scale = 0)
    private BigDecimal succeededAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Column(nullable = false, length = 1000, columnDefinition = "nvarchar(1000)", updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, updatable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundState status;

    @Column(name = "policy_version", length = 80, updatable = false)
    private String policyVersion;

    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128, updatable = false)
    private String requestHash;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PlatformRefundRequest() {
    }

    public static PlatformRefundRequest request(
            PlatformFinancialTransaction originalTransaction,
            SubscriptionOrder order,
            VndMoney amount,
            String reason,
            User requestedBy,
            String policyVersion,
            String idempotencyKey,
            String requestHash,
            LocalDateTime requestedAt) {
        PlatformRefundRequest refund = new PlatformRefundRequest();
        refund.publicId = UUID.randomUUID().toString();
        refund.originalTransaction = Objects.requireNonNull(originalTransaction, "original transaction must not be null");
        refund.order = Objects.requireNonNull(order, "order must not be null");
        refund.requestedAmount = positive(amount);
        refund.reason = requireText(reason, "reason");
        refund.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        refund.policyVersion = policyVersion == null || policyVersion.isBlank() ? null : policyVersion.trim();
        refund.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        refund.requestHash = requireText(requestHash, "requestHash");
        refund.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        refund.status = RefundState.REQUESTED;
        refund.validateOwnership();
        return refund;
    }

    public VndMoney requestedMoney() {
        return VndMoney.of(requestedAmount);
    }

    public void markPolicyBlocked() {
        if (status != RefundState.REQUESTED) throw new IllegalStateException("Refund is not pending policy review.");
        status = RefundState.POLICY_BLOCKED;
    }

    public void approveForProvider(User approver, String approvedPolicyVersion) {
        if (status != RefundState.REQUESTED && status != RefundState.PENDING_APPROVAL) {
            throw new IllegalStateException("Platform refund is not awaiting approval.");
        }
        String approvedVersion = requireText(approvedPolicyVersion, "approvedPolicyVersion");
        if (!approvedVersion.equals(policyVersion)) {
            throw new IllegalStateException("Platform refund policy version does not match the request snapshot.");
        }
        approvedBy = Objects.requireNonNull(approver, "approver must not be null");
        status = RefundState.PENDING_PROVIDER;
    }

    public void markPendingProvider() {
        if (status != RefundState.REQUESTED && status != RefundState.PENDING_APPROVAL) {
            throw new IllegalStateException("Refund transition is not valid from " + status + ".");
        }
        status = RefundState.PENDING_PROVIDER;
    }

    public void markSucceeded(VndMoney amount, LocalDateTime completedAt) {
        if (status != RefundState.PENDING_PROVIDER) throw new IllegalStateException("Refund is not pending provider.");
        succeededAmount = positive(amount);
        status = RefundState.SUCCEEDED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
    }

    public void markFailed(LocalDateTime completedAt) {
        if (status != RefundState.PENDING_PROVIDER) throw new IllegalStateException("Refund is not pending provider.");
        status = RefundState.FAILED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
    }

    @PrePersist
    void validateOnInsert() {
        validateOwnership();
        positive(VndMoney.of(requestedAmount));
        if (!"VND".equals(currency)) throw new IllegalStateException("Platform refunds require VND.");
    }

    private void validateOwnership() {
        if (originalTransaction.getOrder() == null || !sameId(order.getId(), originalTransaction.getOrder().getId())) {
            throw new IllegalArgumentException("Refund and original transaction must belong to the same order.");
        }
        if (originalTransaction.getDirection() != PlatformFinancialTransaction.Direction.DEBIT) {
            throw new IllegalArgumentException("Only platform debit transactions can be refunded.");
        }
    }

    private static BigDecimal positive(VndMoney amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.amount().signum() <= 0) throw new IllegalArgumentException("Refund amount must be positive.");
        return amount.amount();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank.");
        return value.trim();
    }

    private static boolean sameId(Long left, Long right) {
        return left != null && right != null && left.equals(right);
    }
}

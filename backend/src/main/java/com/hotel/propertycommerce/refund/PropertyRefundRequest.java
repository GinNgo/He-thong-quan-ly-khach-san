package com.hotel.propertycommerce.refund;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "property_refund_requests", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_property_refund_public", columnNames = "public_id"),
        @UniqueConstraint(name = "UQ_property_refund_idempotency", columnNames = {"hotel_id", "idempotency_key"})
})
@FilterDef(name = "propertyRefundTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyRefundTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyRefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_transaction_id", nullable = false, updatable = false)
    private PropertyFinancialTransaction originalTransaction;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 19, scale = 0)
    private BigDecimal approvedAmount;

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

    protected PropertyRefundRequest() {
    }

    public static PropertyRefundRequest request(
            Hotel hotel,
            PropertyFinancialTransaction originalTransaction,
            VndMoney amount,
            String reason,
            User requestedBy,
            String idempotencyKey,
            String requestHash,
            LocalDateTime requestedAt) {
        PropertyRefundRequest refund = new PropertyRefundRequest();
        refund.publicId = UUID.randomUUID().toString();
        refund.hotel = Objects.requireNonNull(hotel, "hotel must not be null");
        refund.originalTransaction = Objects.requireNonNull(originalTransaction, "original transaction must not be null");
        refund.requestedAmount = positive(amount);
        refund.reason = requireText(reason, "reason");
        refund.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
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

    public void markPendingApproval(User approver) {
        if (status != RefundState.REQUESTED) throw new IllegalStateException("Refund is not awaiting approval.");
        status = RefundState.PENDING_APPROVAL;
        approvedBy = approver;
        approvedAmount = requestedAmount;
    }

    public void approve(User approver) {
        if (status != RefundState.REQUESTED && status != RefundState.PENDING_APPROVAL) {
            throw new IllegalStateException("Refund is not awaiting approval.");
        }
        approvedBy = Objects.requireNonNull(approver, "approver must not be null");
        approvedAmount = requestedAmount;
        status = RefundState.PENDING_PROVIDER;
    }

    public void markPendingProvider() {
        requireStatus(RefundState.REQUESTED, RefundState.PENDING_APPROVAL);
        status = RefundState.PENDING_PROVIDER;
    }

    public void markSucceeded(VndMoney amount, LocalDateTime completedAt) {
        requireStatus(RefundState.PENDING_PROVIDER, RefundState.REQUESTED, RefundState.PENDING_APPROVAL);
        succeededAmount = positive(amount);
        status = RefundState.SUCCEEDED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
    }

    public void markFailed(LocalDateTime completedAt) {
        requireStatus(RefundState.PENDING_PROVIDER, RefundState.REQUESTED, RefundState.PENDING_APPROVAL);
        status = RefundState.FAILED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
    }

    public void markCancelled(LocalDateTime completedAt) {
        requireStatus(RefundState.REQUESTED, RefundState.PENDING_APPROVAL);
        status = RefundState.CANCELLED;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
    }

    @PrePersist
    void validateOnInsert() {
        validateOwnership();
        positive(VndMoney.of(requestedAmount));
        if (!"VND".equals(currency)) throw new IllegalStateException("Property refunds require VND.");
    }

    private void validateOwnership() {
        if (originalTransaction.getHotel() == null || !sameId(hotel.getId(), originalTransaction.getHotel().getId())) {
            throw new IllegalArgumentException("Refund and original transaction must belong to the same property.");
        }
        if (originalTransaction.getDirection() != PropertyFinancialTransaction.Direction.DEBIT) {
            throw new IllegalArgumentException("Only property debit transactions can be refunded.");
        }
    }

    private void requireStatus(RefundState... allowed) {
        for (RefundState candidate : allowed) if (status == candidate) return;
        throw new IllegalStateException("Refund transition is not valid from " + status + ".");
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

package com.hotel.platformbilling.order;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

@Getter
@Entity(name = "PlatformSubscriptionOrder")
@Table(name = "platform_subscription_orders", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_platform_order_public", columnNames = "public_id"),
        @UniqueConstraint(name = "UQ_platform_order_code", columnNames = "order_code"),
        @UniqueConstraint(name = "UQ_platform_order_idempotency", columnNames = {"owner_user_id", "idempotency_key"})
})
public class SubscriptionOrder {

    public enum Operation {
        PURCHASE,
        RENEW,
        UPGRADE,
        DOWNGRADE,
        REFUND
    }

    public enum DurationUnit {
        DAY,
        MONTH,
        YEAR,
        LIFETIME
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @Column(name = "order_code", nullable = false, length = 80, updatable = false)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_hotel_id", nullable = false, updatable = false)
    private Hotel targetHotel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Operation operation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private SubscriptionPlan plan;

    @Column(name = "plan_version", nullable = false, length = 80, updatable = false)
    private String planVersion;

    @Column(name = "plan_code", nullable = false, length = 80, updatable = false)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 255, columnDefinition = "nvarchar(255)", updatable = false)
    private String planName;

    @Column(nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Column(name = "billing_period", nullable = false, length = 30, updatable = false)
    private String billingPeriod;

    @Column(name = "duration_value", nullable = false, updatable = false)
    private int durationValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", nullable = false, length = 20, updatable = false)
    private DurationUnit durationUnit;

    @Column(name = "feature_snapshot_json", nullable = false, columnDefinition = "nvarchar(max)", updatable = false)
    private String featureSnapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionOrderState status;

    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128, updatable = false)
    private String requestHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SubscriptionOrder() {
    }

    public static SubscriptionOrder create(
            String publicId,
            String orderCode,
            User owner,
            Hotel targetHotel,
            Operation operation,
            SubscriptionPlan plan,
            String planVersion,
            String planCode,
            String planName,
            VndMoney price,
            String billingPeriod,
            int durationValue,
            DurationUnit durationUnit,
            String featureSnapshotJson,
            String idempotencyKey,
            String requestHash,
            LocalDateTime expiresAt) {
        SubscriptionOrder order = new SubscriptionOrder();
        order.publicId = requireText(publicId, "publicId", 64);
        order.orderCode = requireText(orderCode, "orderCode", 80);
        order.owner = Objects.requireNonNull(owner, "owner must not be null");
        order.targetHotel = Objects.requireNonNull(targetHotel, "targetHotel must not be null");
        order.operation = Objects.requireNonNull(operation, "operation must not be null");
        order.plan = Objects.requireNonNull(plan, "plan must not be null");
        order.planVersion = requireText(planVersion, "planVersion", 80);
        order.planCode = normalizeCode(planCode, "planCode", 80);
        order.planName = requireText(planName, "planName", 255);
        order.price = amount(price, "price");
        order.billingPeriod = normalizeCode(billingPeriod, "billingPeriod", 30);
        order.durationValue = durationValue;
        order.durationUnit = Objects.requireNonNull(durationUnit, "durationUnit must not be null");
        order.featureSnapshotJson = requireText(featureSnapshotJson, "featureSnapshotJson", Integer.MAX_VALUE);
        order.status = SubscriptionOrderState.CREATED;
        order.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 160);
        order.requestHash = requireText(requestHash, "requestHash", 128);
        order.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        order.validate();
        return order;
    }

    public VndMoney priceMoney() {
        return VndMoney.of(price);
    }

    public boolean expiredAt(LocalDateTime now) {
        return now != null && !expiresAt.isAfter(now);
    }

    public void transitionTo(SubscriptionOrderState next, LocalDateTime occurredAt) {
        Objects.requireNonNull(next, "next must not be null");
        if (!allowedTransition(status, next)) {
            throw new IllegalStateException("Unsupported subscription order transition: " + status + " -> " + next);
        }
        status = next;
        if (next == SubscriptionOrderState.APPLIED) {
            appliedAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }

    @PrePersist
    void created() {
        validate();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        validate();
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void validate() {
        VndMoney.of(price);
        if (!"VND".equals(currency)) {
            throw new IllegalStateException("Platform subscription orders support VND only.");
        }
        if (durationValue <= 0) {
            throw new IllegalArgumentException("durationValue must be greater than zero.");
        }
        if (status == SubscriptionOrderState.APPLIED && appliedAt == null) {
            throw new IllegalStateException("Applied subscription orders require appliedAt evidence.");
        }
    }

    private static boolean allowedTransition(SubscriptionOrderState current, SubscriptionOrderState next) {
        return switch (current) {
            case CREATED -> next == SubscriptionOrderState.PENDING_PAYMENT
                    || next == SubscriptionOrderState.CANCELLED
                    || next == SubscriptionOrderState.EXPIRED;
            case PENDING_PAYMENT -> next == SubscriptionOrderState.PAID
                    || next == SubscriptionOrderState.FAILED
                    || next == SubscriptionOrderState.CANCELLED
                    || next == SubscriptionOrderState.EXPIRED;
            case PAID -> next == SubscriptionOrderState.APPLIED;
            case APPLIED -> next == SubscriptionOrderState.REFUNDED;
            case FAILED, CANCELLED, EXPIRED, REFUNDED -> false;
        };
    }

    private static BigDecimal amount(VndMoney money, String field) {
        Objects.requireNonNull(money, field + " must not be null");
        return money.amount();
    }

    private static String normalizeCode(String value, String field, int maxLength) {
        return requireText(value, field, maxLength).toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }
}

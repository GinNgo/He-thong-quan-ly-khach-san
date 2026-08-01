package com.hotel.platformbilling.payment;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.platformbilling.order.SubscriptionOrder;
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
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

@Getter
@Immutable
@Entity
@Table(name = "platform_financial_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_platform_transaction_public", columnNames = "public_id"),
        @UniqueConstraint(name = "UQ_platform_transaction_effect", columnNames = "idempotency_identity")
})
public class PlatformFinancialTransaction {

    public enum TransactionType {
        SUBSCRIPTION_PURCHASE,
        SUBSCRIPTION_RENEWAL,
        SUBSCRIPTION_UPGRADE,
        DOWNGRADE_CREDIT,
        SUBSCRIPTION_REFUND
    }

    public enum Direction {
        DEBIT,
        CREDIT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private SubscriptionOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", updatable = false)
    private PlatformPaymentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id", updatable = false)
    private PlatformFinancialTransaction originalTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40, updatable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Direction direction;

    @Column(nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Column(length = 40, updatable = false)
    private String method;

    @Column(length = 40, updatable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private PaymentEnvironment environment;

    @Column(name = "provider_transaction_ref", length = 200, updatable = false)
    private String providerTransactionReference;

    @Column(name = "idempotency_identity", nullable = false, length = 200, updatable = false)
    private String idempotencyIdentity;

    @Column(name = "actor_type", nullable = false, length = 30, updatable = false)
    private String actorType;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(length = 1000, columnDefinition = "nvarchar(1000)", updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    protected PlatformFinancialTransaction() {
    }

    public static PlatformFinancialTransaction record(
            String publicId,
            SubscriptionOrder order,
            PlatformPaymentAttempt attempt,
            PlatformFinancialTransaction originalTransaction,
            TransactionType transactionType,
            Direction direction,
            VndMoney amount,
            String method,
            String provider,
            PaymentEnvironment environment,
            String providerTransactionReference,
            String idempotencyIdentity,
            String actorType,
            Long actorId,
            String reason,
            LocalDateTime occurredAt) {
        PlatformFinancialTransaction transaction = new PlatformFinancialTransaction();
        transaction.publicId = requireText(publicId, "publicId", 64);
        transaction.order = Objects.requireNonNull(order, "order must not be null");
        transaction.attempt = attempt;
        transaction.originalTransaction = originalTransaction;
        transaction.transactionType = Objects.requireNonNull(transactionType, "transactionType must not be null");
        transaction.direction = Objects.requireNonNull(direction, "direction must not be null");
        transaction.amount = requirePositive(amount);
        transaction.method = normalizeCode(method, 40);
        transaction.provider = normalizeCode(provider, 40);
        transaction.environment = environment;
        transaction.providerTransactionReference = normalizeOptional(providerTransactionReference, 200);
        transaction.idempotencyIdentity = requireText(idempotencyIdentity, "idempotencyIdentity", 200);
        transaction.actorType = normalizeCode(requireText(actorType, "actorType", 30), 30);
        transaction.actorId = actorId;
        transaction.reason = normalizeOptional(reason, 1000);
        transaction.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        transaction.validate();
        return transaction;
    }

    public VndMoney money() {
        return VndMoney.of(amount);
    }

    @PrePersist
    void created() {
        validate();
        recordedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Platform financial transactions are append-only.");
    }

    @PreRemove
    void rejectDelete() {
        throw new IllegalStateException("Platform financial transactions cannot be deleted.");
    }

    private void validate() {
        if (!"VND".equals(currency) || amount.signum() <= 0) {
            throw new IllegalStateException("Platform financial transactions require a positive VND amount.");
        }
        if (attempt != null && !sameOrder(attempt.getOrder(), order)) {
            throw new IllegalArgumentException("Attempt and platform transaction must reference the same order.");
        }
        boolean credit = transactionType == TransactionType.DOWNGRADE_CREDIT
                || transactionType == TransactionType.SUBSCRIPTION_REFUND;
        if (credit != (direction == Direction.CREDIT)) {
            throw new IllegalArgumentException("Platform transaction direction does not match its type.");
        }
        if (credit && originalTransaction == null) {
            throw new IllegalArgumentException("Platform credits require an original transaction.");
        }
        if (!credit && originalTransaction != null) {
            throw new IllegalArgumentException("Platform debits cannot reference an original transaction.");
        }
        if (originalTransaction != null && !sameOrder(originalTransaction.getOrder(), order)) {
            throw new IllegalArgumentException("Original transaction must belong to the same subscription order.");
        }
    }

    private static boolean sameOrder(SubscriptionOrder left, SubscriptionOrder right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private static BigDecimal requirePositive(VndMoney money) {
        Objects.requireNonNull(money, "amount must not be null");
        if (money.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero.");
        }
        return money.amount();
    }

    private static String normalizeCode(String value, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("value is too long.");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return normalized;
    }
}

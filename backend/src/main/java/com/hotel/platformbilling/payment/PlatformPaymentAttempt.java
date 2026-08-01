package com.hotel.platformbilling.payment;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
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
@Entity
@Table(name = "platform_payment_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_platform_attempt_public", columnNames = "public_id"),
        @UniqueConstraint(name = "UQ_platform_attempt_idempotency", columnNames = {"order_id", "idempotency_key"})
})
public class PlatformPaymentAttempt {

    public enum Status {
        CREATED,
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED,
        CANCELLED,
        PARTIALLY_REFUNDED,
        REFUNDED,
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private SubscriptionOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "configuration_id", nullable = false, updatable = false)
    private PlatformPaymentConfiguration configuration;

    @Column(nullable = false, length = 40, updatable = false)
    private String provider;

    @Column(nullable = false, length = 40, updatable = false)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentEnvironment environment;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal expectedAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128, updatable = false)
    private String requestHash;

    @Column(name = "provider_order_ref", length = 160)
    private String providerOrderReference;

    @Column(name = "provider_transaction_ref", length = 200)
    private String providerTransactionReference;

    @Column(name = "provider_event_id", length = 200)
    private String providerEventId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PlatformPaymentAttempt() {
    }

    public static PlatformPaymentAttempt create(
            String publicId,
            SubscriptionOrder order,
            PlatformPaymentConfiguration configuration,
            String method,
            VndMoney expectedAmount,
            String idempotencyKey,
            String requestHash,
            LocalDateTime expiresAt) {
        PlatformPaymentAttempt attempt = new PlatformPaymentAttempt();
        attempt.publicId = requireText(publicId, "publicId", 64);
        attempt.order = Objects.requireNonNull(order, "order must not be null");
        attempt.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        attempt.provider = normalizeCode(configuration.getProvider(), "provider", 40);
        attempt.method = normalizeCode(method, "method", 40);
        attempt.environment = Objects.requireNonNull(configuration.getEnvironment(), "environment must not be null");
        attempt.expectedAmount = Objects.requireNonNull(expectedAmount, "expectedAmount must not be null").amount();
        attempt.status = Status.CREATED;
        attempt.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 160);
        attempt.requestHash = requireText(requestHash, "requestHash", 128);
        attempt.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        attempt.validate();
        return attempt;
    }

    public VndMoney expectedMoney() {
        return VndMoney.of(expectedAmount);
    }

    public void markPending(String providerOrderReference) {
        requireStatus(Status.CREATED);
        this.providerOrderReference = normalizeOptional(providerOrderReference, 160);
        status = Status.PENDING;
    }

    public void markProcessing(String providerEventId) {
        if (status != Status.PENDING && status != Status.CREATED) {
            throw new IllegalStateException("Only created or pending attempts can enter processing.");
        }
        this.providerEventId = normalizeOptional(providerEventId, 200);
        status = Status.PROCESSING;
    }

    public void markSucceeded(
            String providerTransactionReference,
            String providerEventId,
            LocalDateTime completedAt) {
        if (status != Status.PENDING && status != Status.PROCESSING && status != Status.CREATED) {
            throw new IllegalStateException("Only active attempts can succeed.");
        }
        this.providerTransactionReference = requireText(
                providerTransactionReference, "providerTransactionReference", 200);
        this.providerEventId = normalizeOptional(providerEventId, 200);
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.failureCode = null;
        status = Status.SUCCESS;
    }

    public void markFailed(String failureCode, LocalDateTime completedAt) {
        if (status == Status.SUCCESS || terminal()) {
            throw new IllegalStateException("Terminal payment attempt cannot fail again.");
        }
        this.failureCode = requireText(failureCode, "failureCode", 100);
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        status = Status.FAILED;
    }

    public void expire(LocalDateTime completedAt) {
        if (status != Status.CREATED && status != Status.PENDING) {
            throw new IllegalStateException("Only created or pending attempts can expire.");
        }
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        status = Status.EXPIRED;
    }

    public boolean terminal() {
        return status == Status.SUCCESS || status == Status.FAILED || status == Status.CANCELLED
                || status == Status.REFUNDED || status == Status.EXPIRED;
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

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected payment attempt status " + expected + " but was " + status + '.');
        }
    }

    private void validate() {
        VndMoney.of(expectedAmount);
        if (!"VND".equals(currency)) {
            throw new IllegalStateException("Platform payment attempts support VND only.");
        }
        if (!provider.equals(configuration.getProvider()) || environment != configuration.getEnvironment()) {
            throw new IllegalStateException("Payment attempt must use its platform configuration identity.");
        }
        if (terminal() && completedAt == null) {
            throw new IllegalStateException("Terminal platform payment attempts require completedAt evidence.");
        }
    }

    private static String normalizeCode(String value, String field, int maxLength) {
        return requireText(value, field, maxLength).toUpperCase(Locale.ROOT);
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

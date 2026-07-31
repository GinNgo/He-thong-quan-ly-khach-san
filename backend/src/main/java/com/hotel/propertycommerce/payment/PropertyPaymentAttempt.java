package com.hotel.propertycommerce.payment;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.FinancialTransitionPolicy;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(name = "property_payment_attempts",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_property_attempt_public", columnNames = "public_id"),
                @UniqueConstraint(name = "UQ_property_attempt_idempotency", columnNames = {"hotel_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "IX_property_attempt_reservation_status", columnList = "hotel_id,reservation_id,status"),
                @Index(name = "IX_property_attempt_expiry", columnList = "hotel_id,status,expires_at")
        })
@FilterDef(name = "propertyPaymentAttemptTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyPaymentAttemptTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyPaymentAttempt {

    public enum Purpose {
        DEPOSIT,
        BALANCE,
        SERVICE,
        SURCHARGE,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id", updatable = false)
    private PropertyPaymentConfiguration configuration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", updatable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Purpose purpose;

    @Column(nullable = false, length = 40, updatable = false)
    private String method;

    @Column(nullable = false, length = 40, updatable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentEnvironment environment;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal expectedAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Column(name = "unique_transfer_content", length = 160, updatable = false)
    private String uniqueTransferContent;

    @Column(name = "receiver_snapshot_json", columnDefinition = "nvarchar(max)", updatable = false)
    private String receiverSnapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentState status = PaymentState.CREATED;

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

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PropertyPaymentAttempt() {
    }

    public static PropertyPaymentAttempt create(
            String publicId,
            Hotel hotel,
            Reservation reservation,
            PropertyPaymentConfiguration configuration,
            User owner,
            Purpose purpose,
            String method,
            String provider,
            PaymentEnvironment environment,
            VndMoney expectedAmount,
            String uniqueTransferContent,
            String receiverSnapshotJson,
            String idempotencyKey,
            String requestHash,
            LocalDateTime expiresAt) {
        PropertyPaymentAttempt attempt = new PropertyPaymentAttempt();
        attempt.publicId = requireText(publicId, "publicId");
        attempt.hotel = Objects.requireNonNull(hotel, "hotel must not be null");
        attempt.reservation = Objects.requireNonNull(reservation, "reservation must not be null");
        attempt.configuration = configuration;
        attempt.owner = owner;
        attempt.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        attempt.method = normalizeCode(method, "method");
        attempt.provider = normalizeCode(provider, "provider");
        attempt.environment = Objects.requireNonNull(environment, "environment must not be null");
        attempt.expectedAmount = requirePositive(expectedAmount, "expectedAmount");
        attempt.uniqueTransferContent = normalizeOptional(uniqueTransferContent);
        attempt.receiverSnapshotJson = normalizeOptional(receiverSnapshotJson);
        attempt.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        attempt.requestHash = requireText(requestHash, "requestHash");
        attempt.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        attempt.validateOwnership();
        return attempt;
    }

    public VndMoney expectedMoney() {
        return VndMoney.of(expectedAmount);
    }

    public boolean transitionTo(PaymentState target, LocalDateTime eventTime, User verifier, String failureCode) {
        Objects.requireNonNull(target, "target state must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        FinancialTransitionPolicy.Decision decision = FinancialTransitionPolicy.payment(status, target);
        if (decision == FinancialTransitionPolicy.Decision.IDEMPOTENT) {
            return false;
        }
        if (decision == FinancialTransitionPolicy.Decision.REJECT) {
            throw new IllegalStateException("Payment attempt cannot transition from " + status + " to " + target + ".");
        }
        if (target == PaymentState.SUCCESS) {
            verifiedAt = eventTime;
            verifiedBy = verifier;
            this.failureCode = null;
        } else if (target == PaymentState.FAILED) {
            this.failureCode = requireText(failureCode, "failureCode");
        }
        status = target;
        return true;
    }

    public void bindProviderOrderReference(String reference) {
        providerOrderReference = bindOnce(providerOrderReference, reference, "providerOrderReference");
    }

    public void bindProviderTransactionReference(String reference) {
        providerTransactionReference = bindOnce(providerTransactionReference, reference, "providerTransactionReference");
    }

    public void bindProviderEventId(String eventId) {
        providerEventId = bindOnce(providerEventId, eventId, "providerEventId");
    }

    @PrePersist
    void created() {
        validateOwnership();
        VndMoney.of(expectedAmount);
        if (expectedAmount.signum() <= 0 || !"VND".equals(currency)) {
            throw new IllegalStateException("Property payment attempts require a positive VND amount.");
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        validateOwnership();
        VndMoney.of(expectedAmount);
        updatedAt = LocalDateTime.now();
    }

    private void validateOwnership() {
        if (!sameEntity(hotel, reservation.getHotel())) {
            throw new IllegalArgumentException("Reservation must belong to the payment-attempt property.");
        }
        if (configuration != null && !sameEntity(hotel, configuration.getHotel())) {
            throw new IllegalArgumentException("Payment configuration must belong to the payment-attempt property.");
        }
    }

    private static boolean sameEntity(Hotel left, Hotel right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private static BigDecimal requirePositive(VndMoney money, String field) {
        Objects.requireNonNull(money, field + " must not be null");
        if (money.amount().signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero.");
        }
        return money.amount();
    }

    private static String bindOnce(String current, String candidate, String field) {
        String normalized = requireText(candidate, field);
        if (current == null) {
            return normalized;
        }
        if (!current.equals(normalized)) {
            throw new IllegalStateException(field + " is immutable once assigned.");
        }
        return current;
    }

    private static String normalizeCode(String value, String field) {
        return requireText(value, field).toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }
}

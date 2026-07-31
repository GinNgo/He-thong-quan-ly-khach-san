package com.hotel.propertycommerce.payment;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.VndMoney;
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
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Getter
@Immutable
@Entity
@Table(name = "property_financial_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_property_transaction_public", columnNames = "public_id"),
                @UniqueConstraint(name = "UQ_property_transaction_effect", columnNames = "idempotency_identity")
        },
        indexes = @Index(name = "IX_property_transactions_hotel_occurred",
                columnList = "hotel_id,occurred_at,transaction_type"))
@FilterDef(name = "propertyFinancialTransactionTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyFinancialTransactionTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyFinancialTransaction {

    public enum TransactionType {
        BOOKING_DEPOSIT,
        ROOM_PAYMENT,
        SERVICE_PAYMENT,
        SURCHARGE,
        MANUAL_ADJUSTMENT,
        REFUND
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
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", updatable = false)
    private Reservation reservation;

    @Column(name = "invoice_id", updatable = false)
    private Long invoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", updatable = false)
    private PropertyPaymentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id", updatable = false)
    private PropertyFinancialTransaction originalTransaction;

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

    protected PropertyFinancialTransaction() {
    }

    public static PropertyFinancialTransaction record(
            String publicId,
            Hotel hotel,
            Reservation reservation,
            Long invoiceId,
            PropertyPaymentAttempt attempt,
            PropertyFinancialTransaction originalTransaction,
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
        PropertyFinancialTransaction transaction = new PropertyFinancialTransaction();
        transaction.publicId = requireText(publicId, "publicId");
        transaction.hotel = Objects.requireNonNull(hotel, "hotel must not be null");
        transaction.reservation = reservation;
        transaction.invoiceId = invoiceId;
        transaction.attempt = attempt;
        transaction.originalTransaction = originalTransaction;
        transaction.transactionType = Objects.requireNonNull(transactionType, "transactionType must not be null");
        transaction.direction = Objects.requireNonNull(direction, "direction must not be null");
        transaction.amount = requirePositive(amount);
        transaction.method = normalizeCode(method);
        transaction.provider = normalizeCode(provider);
        transaction.environment = environment;
        transaction.providerTransactionReference = normalizeOptional(providerTransactionReference);
        transaction.idempotencyIdentity = requireText(idempotencyIdentity, "idempotencyIdentity");
        transaction.actorType = normalizeCode(requireText(actorType, "actorType"));
        transaction.actorId = actorId;
        transaction.reason = normalizeOptional(reason);
        transaction.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        transaction.validateEvidence();
        return transaction;
    }

    public VndMoney money() {
        return VndMoney.of(amount);
    }

    @PrePersist
    void beforeInsert() {
        validateEvidence();
        VndMoney.of(amount);
        if (amount.signum() <= 0 || !"VND".equals(currency)) {
            throw new IllegalStateException("Property ledger transactions require a positive VND amount.");
        }
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Property financial transactions are append-only.");
    }

    @PreRemove
    void rejectDelete() {
        throw new IllegalStateException("Property financial transactions cannot be deleted.");
    }

    private void validateEvidence() {
        if (reservation == null && invoiceId == null) {
            throw new IllegalArgumentException("A property transaction requires a reservation or invoice owner.");
        }
        if (reservation != null && !sameHotel(hotel, reservation.getHotel())) {
            throw new IllegalArgumentException("Reservation must belong to the ledger property.");
        }
        if (attempt != null && !sameHotel(hotel, attempt.getHotel())) {
            throw new IllegalArgumentException("Payment attempt must belong to the ledger property.");
        }
        if (reservation != null && attempt != null && !sameReservation(reservation, attempt.getReservation())) {
            throw new IllegalArgumentException("Payment attempt and transaction must reference the same reservation.");
        }
        if (originalTransaction != null && !sameHotel(hotel, originalTransaction.getHotel())) {
            throw new IllegalArgumentException("Original transaction must belong to the ledger property.");
        }
        if (reservation != null && originalTransaction != null
                && originalTransaction.getReservation() != null
                && !sameReservation(reservation, originalTransaction.getReservation())) {
            throw new IllegalArgumentException("Refund and original transaction must reference the same reservation.");
        }
        if (transactionType == TransactionType.REFUND && originalTransaction == null) {
            throw new IllegalArgumentException("Refund transactions require an original transaction.");
        }
        if (transactionType != TransactionType.REFUND && originalTransaction != null) {
            throw new IllegalArgumentException("Only refund transactions may reference an original transaction.");
        }
    }

    private static boolean sameHotel(Hotel left, Hotel right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private static boolean sameReservation(Reservation left, Reservation right) {
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

    private static String normalizeCode(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
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

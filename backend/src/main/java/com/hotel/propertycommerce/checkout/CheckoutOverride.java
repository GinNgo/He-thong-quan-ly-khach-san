package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
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
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Getter
@Entity
@Table(name = "checkout_overrides")
@FilterDef(name = "checkoutOverrideTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "checkoutOverrideTenantFilter", condition = "hotel_id = :hotelId")
public class CheckoutOverride {

    public enum OverrideType {
        DEBT,
        OVERPAYMENT,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_type", nullable = false, length = 30, updatable = false)
    private OverrideType overrideType;

    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal outstandingAmount;

    @Column(nullable = false, length = 1000, columnDefinition = "nvarchar(1000)", updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", updatable = false)
    private User approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CheckoutOverride() {
    }

    public static CheckoutOverride approveDebt(
            Hotel hotel,
            Reservation reservation,
            BigDecimal outstandingAmount,
            String reason,
            User actor) {
        CheckoutOverride override = new CheckoutOverride();
        override.hotel = Objects.requireNonNull(hotel, "hotel must not be null");
        override.reservation = Objects.requireNonNull(reservation, "reservation must not be null");
        override.overrideType = OverrideType.DEBT;
        override.outstandingAmount = requirePositiveVnd(outstandingAmount);
        override.reason = requireReason(reason);
        override.actor = Objects.requireNonNull(actor, "actor must not be null");
        override.approvedBy = actor;
        override.validateOwnership();
        return override;
    }

    public VndMoney outstandingMoney() {
        return VndMoney.of(outstandingAmount);
    }

    @PrePersist
    void created() {
        validateOwnership();
        requirePositiveVnd(outstandingAmount);
        requireReason(reason);
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Checkout overrides are append-only and cannot be updated.");
    }

    private void validateOwnership() {
        if (reservation.getHotel() == null || !sameId(hotel.getId(), reservation.getHotel().getId())) {
            throw new IllegalArgumentException("Reservation must belong to the checkout-override property.");
        }
        if (approvedBy == null) {
            throw new IllegalArgumentException("A debt override requires an approving actor.");
        }
    }

    private static BigDecimal requirePositiveVnd(BigDecimal amount) {
        try {
            BigDecimal normalized = VndMoney.of(amount).amount();
            if (normalized.signum() <= 0 || normalized.precision() > 19) {
                throw new ArithmeticException("amount outside supported range");
            }
            return normalized;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "outstandingAmount must be a positive integer VND value.", exception);
        }
    }

    private static String requireReason(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Debt override reason is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("Debt override reason is too long.");
        }
        return normalized;
    }

    private static boolean sameId(Long left, Long right) {
        return left != null && left.equals(right);
    }
}

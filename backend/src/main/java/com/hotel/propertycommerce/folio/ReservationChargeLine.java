package com.hotel.propertycommerce.folio;

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
import jakarta.persistence.Index;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(name = "reservation_charge_lines", indexes = @Index(
        name = "IX_charge_lines_hotel_reservation",
        columnList = "hotel_id,reservation_id,created_at"))
@FilterDef(name = "reservationChargeLineTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "reservationChargeLineTenantFilter", condition = "hotel_id = :hotelId")
public class ReservationChargeLine {

    public enum ChargeType {
        ROOM,
        SERVICE,
        MINIBAR,
        SURCHARGE,
        TAX,
        FEE,
        DISCOUNT,
        ADJUSTMENT
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
    @Column(name = "charge_type", nullable = false, length = 30, updatable = false)
    private ChargeType chargeType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(name = "source_version", length = 80, updatable = false)
    private String sourceVersion;

    @Column(length = 80, updatable = false)
    private String code;

    @Column(nullable = false, length = 255, updatable = false)
    private String name;

    @Column(length = 1000, updatable = false)
    private String description;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "service_used_at", updatable = false)
    private LocalDateTime serviceUsedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_line_id", updatable = false)
    private ReservationChargeLine reversesLine;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReservationChargeLine() {
    }

    public static ReservationChargeLine create(
            Hotel hotel,
            Reservation reservation,
            ChargeType chargeType,
            Long sourceId,
            String sourceVersion,
            String code,
            String name,
            String description,
            BigDecimal unitPrice,
            BigDecimal quantity,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            LocalDateTime serviceUsedAt,
            User actor,
            ReservationChargeLine reversesLine) {
        ReservationChargeLine line = new ReservationChargeLine();
        line.hotel = Objects.requireNonNull(hotel, "hotel must not be null");
        line.reservation = Objects.requireNonNull(reservation, "reservation must not be null");
        line.chargeType = Objects.requireNonNull(chargeType, "chargeType must not be null");
        line.sourceId = requirePositiveOptional(sourceId, "sourceId");
        line.sourceVersion = normalizeOptional(sourceVersion);
        line.code = normalizeOptional(code);
        line.name = requireText(name, "name");
        line.description = normalizeOptional(description);
        line.unitPrice = requireVnd(unitPrice, "unitPrice");
        line.quantity = requireQuantity(quantity);
        line.taxAmount = requireVnd(taxAmount, "taxAmount");
        line.discountAmount = requireVnd(discountAmount, "discountAmount");
        line.totalAmount = requireVnd(totalAmount, "totalAmount");
        line.serviceUsedAt = serviceUsedAt;
        line.actor = actor;
        line.reversesLine = reversesLine;
        line.validate();
        return line;
    }

    public VndMoney unitPriceMoney() {
        return VndMoney.of(unitPrice);
    }

    public VndMoney taxMoney() {
        return VndMoney.of(taxAmount);
    }

    public VndMoney discountMoney() {
        return VndMoney.of(discountAmount);
    }

    public VndMoney totalMoney() {
        return VndMoney.of(totalAmount);
    }

    @PrePersist
    void created() {
        validate();
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Reservation charge lines are append-only and cannot be updated.");
    }

    private void validate() {
        if (!sameHotel(hotel, reservation.getHotel())) {
            throw new IllegalArgumentException("Reservation must belong to the charge-line property.");
        }
        if ((chargeType == ChargeType.SERVICE || chargeType == ChargeType.MINIBAR) && serviceUsedAt == null) {
            throw new IllegalArgumentException("Service and minibar charges require a usage timestamp.");
        }
        if (reversesLine != null) {
            if (reversesLine == this) {
                throw new IllegalArgumentException("A charge line cannot reverse itself.");
            }
            if (!sameHotel(hotel, reversesLine.hotel)
                    || !sameReservation(reservation, reversesLine.reservation)) {
                throw new IllegalArgumentException("A reversal must reference a charge from the same reservation.");
            }
        }
        requireVnd(unitPrice, "unitPrice");
        requireQuantity(quantity);
        requireVnd(taxAmount, "taxAmount");
        requireVnd(discountAmount, "discountAmount");
        requireVnd(totalAmount, "totalAmount");
    }

    private static boolean sameHotel(Hotel left, Hotel right) {
        if (left == null || right == null) return false;
        if (left.getId() != null && right.getId() != null) return left.getId().equals(right.getId());
        return left == right;
    }

    private static boolean sameReservation(Reservation left, Reservation right) {
        if (left == null || right == null) return false;
        if (left.getId() != null && right.getId() != null) return left.getId().equals(right.getId());
        return left == right;
    }

    private static BigDecimal requireVnd(BigDecimal amount, String field) {
        if (amount == null) throw new IllegalArgumentException(field + " must not be null.");
        try {
            return VndMoney.of(amount).amount();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalArgumentException(field + " must be a non-negative integer VND amount.", exception);
        }
    }

    private static BigDecimal requireQuantity(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("quantity must not be null.");
        BigDecimal normalized;
        try {
            normalized = value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("quantity supports at most three decimal places.", exception);
        }
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero.");
        }
        return normalized;
    }

    private static Long requirePositiveOptional(Long value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + " must be positive when provided.");
        }
        return value;
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

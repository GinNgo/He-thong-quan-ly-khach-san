package com.hotel.propertycommerce.invoice;

import com.hotel.entities.Hotel;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.folio.ReservationChargeLine;
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
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Getter
@Immutable
@Entity
@Table(name = "property_invoice_lines")
@FilterDef(name = "propertyInvoiceLineTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyInvoiceLineTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyInvoiceLine {

    public enum LineType {
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
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private PropertyInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 30, updatable = false)
    private LineType lineType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_charge_line_id", updatable = false)
    private ReservationChargeLine sourceChargeLine;

    @Column(length = 80, updatable = false)
    private String code;

    @Column(nullable = false, length = 255, columnDefinition = "nvarchar(255)", updatable = false)
    private String name;

    @Column(length = 1000, columnDefinition = "nvarchar(1000)", updatable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "usage_started_at", updatable = false)
    private LocalDateTime usageStartedAt;

    @Column(name = "usage_ended_at", updatable = false)
    private LocalDateTime usageEndedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PropertyInvoiceLine() {
    }

    public static PropertyInvoiceLine snapshot(
            PropertyInvoice invoice,
            LineType lineType,
            ReservationChargeLine sourceChargeLine,
            String code,
            String name,
            String description,
            BigDecimal quantity,
            VndMoney unitPrice,
            VndMoney taxAmount,
            VndMoney discountAmount,
            VndMoney totalAmount,
            LocalDateTime usageStartedAt,
            LocalDateTime usageEndedAt) {
        PropertyInvoiceLine line = new PropertyInvoiceLine();
        line.invoice = Objects.requireNonNull(invoice, "invoice must not be null");
        line.hotel = Objects.requireNonNull(invoice.getHotel(), "invoice hotel must not be null");
        line.lineType = Objects.requireNonNull(lineType, "lineType must not be null");
        line.sourceChargeLine = sourceChargeLine;
        line.code = normalizeOptional(code, 80);
        line.name = requireText(name, "name", 255);
        line.description = normalizeOptional(description, 1000);
        line.quantity = quantity(quantity);
        line.unitPrice = amount(unitPrice, "unitPrice");
        line.taxAmount = amount(taxAmount, "taxAmount");
        line.discountAmount = amount(discountAmount, "discountAmount");
        line.totalAmount = amount(totalAmount, "totalAmount");
        line.usageStartedAt = usageStartedAt;
        line.usageEndedAt = usageEndedAt;
        line.validate();
        return line;
    }

    @PrePersist
    void created() {
        validate();
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Property invoice lines are immutable and cannot be updated.");
    }

    @PreRemove
    void rejectDelete() {
        throw new IllegalStateException("Property invoice lines cannot be deleted.");
    }

    private void validate() {
        if (!sameHotel(hotel, invoice.getHotel())) {
            throw new IllegalArgumentException("Invoice line must belong to the invoice property.");
        }
        if (invoice.getStatus() != PropertyInvoice.Status.FINALIZED) {
            throw new IllegalArgumentException("Invoice lines require a finalized invoice snapshot.");
        }
        if (sourceChargeLine != null) {
            if (!sameHotel(hotel, sourceChargeLine.getHotel())
                    || !sameReservation(invoice, sourceChargeLine)) {
                throw new IllegalArgumentException("Source charge line must belong to the same property and reservation.");
            }
        }
        if (usageStartedAt != null && usageEndedAt != null && usageEndedAt.isBefore(usageStartedAt)) {
            throw new IllegalArgumentException("Invoice line usage end cannot precede usage start.");
        }
        BigDecimal expected;
        if (lineType == LineType.DISCOUNT) {
            if (unitPrice.signum() != 0 || taxAmount.signum() != 0
                    || discountAmount.signum() <= 0 || totalAmount.compareTo(discountAmount) != 0) {
                throw new IllegalArgumentException("Discount invoice lines must store one positive discount magnitude.");
            }
            return;
        }
        expected = unitPrice.multiply(quantity).add(taxAmount).subtract(discountAmount);
        if (expected.signum() < 0 || expected.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException("Invoice line total does not match its immutable price snapshot.");
        }
    }

    private static boolean sameReservation(PropertyInvoice invoice, ReservationChargeLine source) {
        if (invoice.getReservation() == null || source.getReservation() == null) {
            return false;
        }
        Long invoiceReservationId = invoice.getReservation().getId();
        Long sourceReservationId = source.getReservation().getId();
        if (invoiceReservationId != null && sourceReservationId != null) {
            return invoiceReservationId.equals(sourceReservationId);
        }
        return invoice.getReservation() == source.getReservation();
    }

    private static BigDecimal quantity(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("quantity must not be null.");
        }
        try {
            BigDecimal normalized = value.setScale(3, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 19) {
                throw new ArithmeticException("quantity outside supported range");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("quantity must be positive with at most three decimal places.", exception);
        }
    }

    private static BigDecimal amount(VndMoney money, String field) {
        Objects.requireNonNull(money, field + " must not be null");
        if (money.amount().precision() > 19) {
            throw new IllegalArgumentException(field + " exceeds the supported VND range.");
        }
        return money.amount();
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

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Invoice line text is too long.");
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

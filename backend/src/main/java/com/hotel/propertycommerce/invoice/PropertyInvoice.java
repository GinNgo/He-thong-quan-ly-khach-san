package com.hotel.propertycommerce.invoice;

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
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Getter
@Immutable
@Entity
@Table(name = "property_invoices", uniqueConstraints = @UniqueConstraint(
        name = "UQ_property_invoice_number",
        columnNames = "invoice_number"))
@FilterDef(name = "propertyInvoiceTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyInvoiceTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyInvoice {

    public enum Status {
        DRAFT,
        FINALIZED,
        CREDITED
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

    @Column(name = "invoice_number", nullable = false, length = 80, updatable = false)
    private String invoiceNumber;

    @Column(name = "customer_snapshot_json", nullable = false, columnDefinition = "nvarchar(max)", updatable = false)
    private String customerSnapshotJson;

    @Column(name = "property_snapshot_json", nullable = false, columnDefinition = "nvarchar(max)", updatable = false)
    private String propertySnapshotJson;

    @Column(nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal taxAmount;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal feeAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal paidAmount;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal refundedAmount;

    @Column(name = "balance_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal balanceAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Status status;

    @Column(name = "finalized_at", nullable = false, updatable = false)
    private LocalDateTime finalizedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finalized_by", nullable = false, updatable = false)
    private User finalizedBy;

    @Version
    @Column(nullable = false, updatable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PropertyInvoice() {
    }

    public static PropertyInvoice finalized(
            Hotel hotel,
            Reservation reservation,
            String invoiceNumber,
            String customerSnapshotJson,
            String propertySnapshotJson,
            VndMoney subtotal,
            VndMoney taxAmount,
            VndMoney feeAmount,
            VndMoney discountAmount,
            VndMoney totalAmount,
            VndMoney paidAmount,
            VndMoney refundedAmount,
            VndMoney balanceAmount,
            User finalizedBy,
            LocalDateTime finalizedAt) {
        PropertyInvoice invoice = new PropertyInvoice();
        invoice.hotel = Objects.requireNonNull(hotel, "hotel must not be null");
        invoice.reservation = Objects.requireNonNull(reservation, "reservation must not be null");
        invoice.invoiceNumber = requireText(invoiceNumber, "invoiceNumber", 80);
        invoice.customerSnapshotJson = requireText(customerSnapshotJson, "customerSnapshotJson", Integer.MAX_VALUE);
        invoice.propertySnapshotJson = requireText(propertySnapshotJson, "propertySnapshotJson", Integer.MAX_VALUE);
        invoice.subtotal = amount(subtotal, "subtotal");
        invoice.taxAmount = amount(taxAmount, "taxAmount");
        invoice.feeAmount = amount(feeAmount, "feeAmount");
        invoice.discountAmount = amount(discountAmount, "discountAmount");
        invoice.totalAmount = amount(totalAmount, "totalAmount");
        invoice.paidAmount = amount(paidAmount, "paidAmount");
        invoice.refundedAmount = amount(refundedAmount, "refundedAmount");
        invoice.balanceAmount = amount(balanceAmount, "balanceAmount");
        invoice.status = Status.FINALIZED;
        invoice.finalizedBy = Objects.requireNonNull(finalizedBy, "finalizedBy must not be null");
        invoice.finalizedAt = Objects.requireNonNull(finalizedAt, "finalizedAt must not be null");
        invoice.validate();
        return invoice;
    }

    public VndMoney totalMoney() {
        return VndMoney.of(totalAmount);
    }

    public VndMoney balanceMoney() {
        return VndMoney.of(balanceAmount);
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
        throw new IllegalStateException("Finalized property invoices are immutable and cannot be updated.");
    }

    @PreRemove
    void rejectDelete() {
        throw new IllegalStateException("Finalized property invoices cannot be deleted.");
    }

    private void validate() {
        if (!sameHotel(hotel, reservation.getHotel())) {
            throw new IllegalArgumentException("Reservation must belong to the invoice property.");
        }
        if (status != Status.FINALIZED || finalizedAt == null || finalizedBy == null) {
            throw new IllegalArgumentException("A property invoice must be created as a finalized snapshot.");
        }
        if (!"VND".equals(currency)) {
            throw new IllegalArgumentException("Property invoices support VND only.");
        }
        BigDecimal expectedTotal = subtotal.add(taxAmount).add(feeAmount).subtract(discountAmount);
        if (expectedTotal.signum() < 0 || expectedTotal.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException("Invoice total does not match subtotal, tax, fee and discount snapshots.");
        }
        if (refundedAmount.compareTo(paidAmount) > 0) {
            throw new IllegalArgumentException("Invoice refunds cannot exceed successful payments.");
        }
        BigDecimal netPaid = paidAmount.subtract(refundedAmount);
        if (netPaid.compareTo(totalAmount.subtract(balanceAmount)) != 0) {
            throw new IllegalArgumentException("Invoice balance does not reconcile with payment and refund snapshots.");
        }
    }

    private static BigDecimal amount(VndMoney money, String field) {
        Objects.requireNonNull(money, field + " must not be null");
        BigDecimal amount = money.amount();
        if (amount.precision() > 19) {
            throw new IllegalArgumentException(field + " exceeds the supported VND range.");
        }
        return amount;
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

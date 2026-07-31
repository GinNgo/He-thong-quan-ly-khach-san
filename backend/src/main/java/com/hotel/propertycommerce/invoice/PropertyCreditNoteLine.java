package com.hotel.propertycommerce.invoice;

import com.hotel.entities.Hotel;
import com.hotel.paymentprovider.domain.VndMoney;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Objects;

@Getter
@Immutable
@Entity
@Table(name = "property_credit_note_lines")
@FilterDef(name = "propertyCreditNoteLineTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyCreditNoteLineTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyCreditNoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false, updatable = false)
    private PropertyCreditNote creditNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_line_id", updatable = false)
    private PropertyInvoiceLine invoiceLine;

    @Column(nullable = false, length = 1000, columnDefinition = "nvarchar(1000)", updatable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal amount;

    protected PropertyCreditNoteLine() {
    }

    public static PropertyCreditNoteLine snapshot(
            PropertyCreditNote creditNote,
            PropertyInvoiceLine invoiceLine,
            String description,
            VndMoney amount) {
        PropertyCreditNoteLine line = new PropertyCreditNoteLine();
        line.creditNote = Objects.requireNonNull(creditNote, "creditNote must not be null");
        line.hotel = Objects.requireNonNull(creditNote.getHotel(), "creditNote hotel must not be null");
        line.invoiceLine = invoiceLine;
        line.description = requireText(description, 1000);
        line.amount = positiveAmount(amount);
        line.validate();
        return line;
    }

    public VndMoney money() {
        return VndMoney.of(amount);
    }

    @PrePersist
    void created() {
        validate();
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Property credit-note lines are immutable and cannot be updated.");
    }

    @PreRemove
    void rejectDelete() {
        throw new IllegalStateException("Property credit-note lines cannot be deleted.");
    }

    private void validate() {
        if (!sameHotel(hotel, creditNote.getHotel())) {
            throw new IllegalArgumentException("Credit-note line must belong to the credit-note property.");
        }
        if (invoiceLine == null) {
            return;
        }
        if (!sameHotel(hotel, invoiceLine.getHotel()) || !sameInvoice(creditNote, invoiceLine)) {
            throw new IllegalArgumentException("Referenced invoice line must belong to the corrected invoice and property.");
        }
        if (invoiceLine.economicEffect().signum() <= 0) {
            throw new IllegalArgumentException("Only a positive invoice charge can be credited.");
        }
        if (amount.compareTo(invoiceLine.economicEffect()) > 0) {
            throw new IllegalArgumentException("Credit-note line cannot exceed the referenced invoice line.");
        }
    }

    private static boolean sameInvoice(PropertyCreditNote note, PropertyInvoiceLine line) {
        Long noteInvoiceId = note.getInvoice() == null ? null : note.getInvoice().getId();
        Long lineInvoiceId = line.getInvoice() == null ? null : line.getInvoice().getId();
        if (noteInvoiceId != null && lineInvoiceId != null) {
            return noteInvoiceId.equals(lineInvoiceId);
        }
        return note.getInvoice() == line.getInvoice();
    }

    private static BigDecimal positiveAmount(VndMoney money) {
        Objects.requireNonNull(money, "amount must not be null");
        BigDecimal value = money.amount();
        if (value.signum() <= 0 || value.precision() > 19) {
            throw new IllegalArgumentException("Credit-note line amount must be a positive supported VND value.");
        }
        return value;
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

    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("description must not be blank.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("description is too long.");
        }
        return normalized;
    }
}

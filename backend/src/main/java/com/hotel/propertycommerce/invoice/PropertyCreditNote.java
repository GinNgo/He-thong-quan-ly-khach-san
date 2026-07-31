package com.hotel.propertycommerce.invoice;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
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
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "property_credit_notes", uniqueConstraints = @UniqueConstraint(
        name = "UQ_property_credit_note_number",
        columnNames = "credit_note_number"))
@FilterDef(name = "propertyCreditNoteTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyCreditNoteTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyCreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private PropertyInvoice invoice;

    @Column(name = "credit_note_number", nullable = false, length = 80, updatable = false)
    private String creditNoteNumber;

    @Column(nullable = false, length = 1000, columnDefinition = "nvarchar(1000)", updatable = false)
    private String reason;

    @Column(nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", updatable = false)
    private User approvedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    protected PropertyCreditNote() {
    }

    public static PropertyCreditNote issue(
            PropertyInvoice invoice,
            String creditNoteNumber,
            String reason,
            VndMoney amount,
            User actor,
            User approvedBy,
            LocalDateTime issuedAt) {
        PropertyCreditNote note = new PropertyCreditNote();
        note.invoice = Objects.requireNonNull(invoice, "invoice must not be null");
        note.hotel = Objects.requireNonNull(invoice.getHotel(), "invoice hotel must not be null");
        note.creditNoteNumber = requireText(creditNoteNumber, "creditNoteNumber", 80);
        note.reason = requireText(reason, "reason", 1000);
        note.amount = positiveAmount(amount);
        note.actor = Objects.requireNonNull(actor, "actor must not be null");
        note.approvedBy = Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        note.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        note.validate();
        return note;
    }

    public VndMoney money() {
        return VndMoney.of(amount);
    }

    @PrePersist
    void created() {
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        validate();
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Property credit notes are immutable and cannot be updated.");
    }

    @PreRemove
    void rejectDelete() {
        throw new IllegalStateException("Property credit notes cannot be deleted.");
    }

    private void validate() {
        if (invoice.getStatus() != PropertyInvoice.Status.FINALIZED) {
            throw new IllegalArgumentException("Credit notes require a finalized invoice.");
        }
        if (!sameHotel(hotel, invoice.getHotel())) {
            throw new IllegalArgumentException("Credit note must belong to the invoice property.");
        }
    }

    private static BigDecimal positiveAmount(VndMoney money) {
        Objects.requireNonNull(money, "amount must not be null");
        BigDecimal value = money.amount();
        if (value.signum() <= 0 || value.precision() > 19) {
            throw new IllegalArgumentException("Credit-note amount must be a positive supported VND value.");
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

package com.hotel.propertycommerce.invoice;

import com.hotel.entities.Hotel;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
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
@Table(name = "property_invoice_payment_allocations", uniqueConstraints = @UniqueConstraint(
        name = "UQ_property_invoice_allocation",
        columnNames = {"invoice_id", "transaction_id"}))
@FilterDef(name = "propertyInvoiceAllocationTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyInvoiceAllocationTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyInvoicePaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private PropertyInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private PropertyFinancialTransaction financialTransaction;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal allocatedAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PropertyInvoicePaymentAllocation() {
    }

    public static PropertyInvoicePaymentAllocation allocate(
            PropertyInvoice invoice,
            PropertyFinancialTransaction transaction,
            VndMoney allocatedAmount) {
        PropertyInvoicePaymentAllocation allocation = new PropertyInvoicePaymentAllocation();
        allocation.invoice = Objects.requireNonNull(invoice, "invoice must not be null");
        allocation.hotel = Objects.requireNonNull(invoice.getHotel(), "invoice hotel must not be null");
        allocation.financialTransaction = Objects.requireNonNull(transaction, "transaction must not be null");
        allocation.allocatedAmount = positiveAmount(allocatedAmount);
        allocation.validate();
        return allocation;
    }

    public VndMoney allocatedMoney() {
        return VndMoney.of(allocatedAmount);
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
        throw new IllegalStateException("Invoice payment allocations are immutable and cannot be updated.");
    }

    @PreRemove
    void rejectDelete() {
        throw new IllegalStateException("Invoice payment allocations cannot be deleted.");
    }

    private void validate() {
        if (invoice.getStatus() != PropertyInvoice.Status.FINALIZED) {
            throw new IllegalArgumentException("Payment allocation requires a finalized invoice.");
        }
        if (!sameHotel(hotel, invoice.getHotel()) || !sameHotel(hotel, financialTransaction.getHotel())) {
            throw new IllegalArgumentException("Invoice and transaction must belong to the allocation property.");
        }
        if (financialTransaction.getReservation() != null
                && !sameReservation(invoice, financialTransaction)) {
            throw new IllegalArgumentException("Invoice and transaction must reference the same reservation.");
        }
        if (financialTransaction.getDirection() != PropertyFinancialTransaction.Direction.DEBIT
                || financialTransaction.getTransactionType() == PropertyFinancialTransaction.TransactionType.REFUND) {
            throw new IllegalArgumentException("Only successful property payment debits can be allocated.");
        }
        if (allocatedAmount.compareTo(financialTransaction.getAmount()) > 0) {
            throw new IllegalArgumentException("Allocated amount cannot exceed the source transaction amount.");
        }
    }

    private static boolean sameReservation(
            PropertyInvoice invoice,
            PropertyFinancialTransaction transaction) {
        Long invoiceReservationId = invoice.getReservation() == null ? null : invoice.getReservation().getId();
        Long transactionReservationId = transaction.getReservation() == null
                ? null
                : transaction.getReservation().getId();
        if (invoiceReservationId != null && transactionReservationId != null) {
            return invoiceReservationId.equals(transactionReservationId);
        }
        return invoice.getReservation() == transaction.getReservation();
    }

    private static BigDecimal positiveAmount(VndMoney money) {
        Objects.requireNonNull(money, "allocatedAmount must not be null");
        BigDecimal amount = money.amount();
        if (amount.signum() <= 0 || amount.precision() > 19) {
            throw new IllegalArgumentException("allocatedAmount must be a positive supported VND amount.");
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
}

package com.hotel.propertycommerce.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.propertycommerce.invoice.PropertyCreditNote;
import com.hotel.propertycommerce.invoice.PropertyCreditNoteLine;
import com.hotel.propertycommerce.invoice.PropertyInvoice;
import com.hotel.propertycommerce.invoice.PropertyInvoiceLine;
import com.hotel.propertycommerce.invoice.PropertyInvoicePaymentAllocation;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Reads authoritative property ledger, invoice and correction evidence for reporting. */
@Repository
@Transactional(readOnly = true)
public class PropertyRevenueRepository {

    private final EntityManager entityManager;

    public PropertyRevenueRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public PropertyRevenueSource load(NormalizedFilters filters) {
        QueryScope scope = QueryScope.from(filters);
        return new PropertyRevenueSource(
                transactions(scope),
                invoices(scope),
                invoiceLines(scope),
                allocations(scope),
                creditNotes(scope),
                creditNoteLines(scope));
    }

    private List<TransactionSource> transactions(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select transaction.publicId,
                       transaction.occurredAt,
                       transaction.transactionType,
                       transaction.direction,
                       transaction.amount,
                       transaction.method,
                       transaction.provider,
                       transaction.reservation.id,
                       transaction.invoiceId,
                       transaction.originalTransaction.publicId
                from PropertyFinancialTransaction transaction
                where transaction.hotel.id = :propertyId
                  and transaction.occurredAt >= :fromInclusive
                  and transaction.occurredAt < :toExclusive
                  and transaction.legacyReconciliationRequired = false
                """);
        appendDirectPaymentFilters(jpql, "transaction", scope);
        appendRoomTypeFilter(jpql, "transaction.reservation", scope);
        jpql.append(" order by transaction.occurredAt, transaction.id");

        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
        bindBase(query, scope);
        bindPaymentFilters(query, scope);
        bindRoomType(query, scope);
        // Project the immutable ledger values directly: reports can contain many rows and do not need entities.
        return query.getResultList().stream().map(row -> new TransactionSource(
                (String) row[0],
                (LocalDateTime) row[1],
                (PropertyFinancialTransaction.TransactionType) row[2],
                (PropertyFinancialTransaction.Direction) row[3],
                (BigDecimal) row[4],
                (String) row[5],
                (String) row[6],
                (Long) row[7],
                (Long) row[8],
                (String) row[9])).toList();
    }

    private List<InvoiceSource> invoices(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select invoice
                from PropertyInvoice invoice
                where invoice.hotel.id = :propertyId
                  and invoice.finalizedAt >= :fromInclusive
                  and invoice.finalizedAt < :toExclusive
                  and invoice.status in :invoiceStatuses
                """);
        appendInvoicePaymentFilters(jpql, "invoice", scope);
        appendRoomTypeFilter(jpql, "invoice.reservation", scope);
        jpql.append(" order by invoice.finalizedAt, invoice.id");

        TypedQuery<PropertyInvoice> query = entityManager.createQuery(jpql.toString(), PropertyInvoice.class);
        bindBase(query, scope);
        bindInvoiceStatuses(query);
        bindPaymentFilters(query, scope);
        bindRoomType(query, scope);
        return query.getResultList().stream().map(invoice -> new InvoiceSource(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getReservation().getId(),
                invoice.getFinalizedAt(),
                invoice.getSubtotal(),
                invoice.getTaxAmount(),
                invoice.getFeeAmount(),
                invoice.getDiscountAmount(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getRefundedAmount(),
                invoice.getBalanceAmount())).toList();
    }

    private List<InvoiceLineSource> invoiceLines(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select line
                from PropertyInvoiceLine line
                join line.invoice invoice
                where line.hotel.id = :propertyId
                  and invoice.finalizedAt >= :fromInclusive
                  and invoice.finalizedAt < :toExclusive
                  and invoice.status in :invoiceStatuses
                """);
        appendInvoicePaymentFilters(jpql, "invoice", scope);
        appendRoomTypeFilter(jpql, "invoice.reservation", scope);
        jpql.append(" order by invoice.finalizedAt, invoice.id, line.id");

        TypedQuery<PropertyInvoiceLine> query = entityManager.createQuery(
                jpql.toString(), PropertyInvoiceLine.class);
        bindBase(query, scope);
        bindInvoiceStatuses(query);
        bindPaymentFilters(query, scope);
        bindRoomType(query, scope);
        return query.getResultList().stream().map(line -> new InvoiceLineSource(
                line.getId(),
                line.getInvoice().getId(),
                line.getLineType(),
                line.getCode(),
                line.getName(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getTaxAmount(),
                line.getDiscountAmount(),
                line.getTotalAmount(),
                line.economicEffect())).toList();
    }

    private List<AllocationSource> allocations(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select allocation
                from PropertyInvoicePaymentAllocation allocation
                join allocation.invoice invoice
                join allocation.financialTransaction transaction
                where allocation.hotel.id = :propertyId
                  and invoice.status in :invoiceStatuses
                  and ((invoice.finalizedAt >= :fromInclusive and invoice.finalizedAt < :toExclusive)
                    or (transaction.occurredAt >= :fromInclusive and transaction.occurredAt < :toExclusive))
                """);
        appendDirectPaymentFilters(jpql, "transaction", scope);
        appendRoomTypeFilter(jpql, "invoice.reservation", scope);
        jpql.append(" order by invoice.finalizedAt, invoice.id, allocation.id");

        TypedQuery<PropertyInvoicePaymentAllocation> query = entityManager.createQuery(
                jpql.toString(), PropertyInvoicePaymentAllocation.class);
        bindBase(query, scope);
        bindInvoiceStatuses(query);
        bindPaymentFilters(query, scope);
        bindRoomType(query, scope);
        return query.getResultList().stream().map(allocation -> new AllocationSource(
                allocation.getId(),
                allocation.getInvoice().getId(),
                allocation.getFinancialTransaction().getPublicId(),
                allocation.getFinancialTransaction().getTransactionType(),
                allocation.getFinancialTransaction().getAmount(),
                allocation.getFinancialTransaction().getOccurredAt(),
                allocation.getAllocatedAmount())).toList();
    }

    private List<CreditNoteSource> creditNotes(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select note
                from PropertyCreditNote note
                join note.invoice invoice
                where note.hotel.id = :propertyId
                  and note.issuedAt >= :fromInclusive
                  and note.issuedAt < :toExclusive
                  and invoice.status in :invoiceStatuses
                """);
        appendInvoicePaymentFilters(jpql, "invoice", scope);
        appendRoomTypeFilter(jpql, "invoice.reservation", scope);
        jpql.append(" order by note.issuedAt, note.id");

        TypedQuery<PropertyCreditNote> query = entityManager.createQuery(jpql.toString(), PropertyCreditNote.class);
        bindBase(query, scope);
        bindInvoiceStatuses(query);
        bindPaymentFilters(query, scope);
        bindRoomType(query, scope);
        return query.getResultList().stream().map(note -> new CreditNoteSource(
                note.getId(),
                note.getCreditNoteNumber(),
                note.getInvoice().getId(),
                note.getIssuedAt(),
                note.getAmount())).toList();
    }

    private List<CreditNoteLineSource> creditNoteLines(QueryScope scope) {
        StringBuilder jpql = new StringBuilder("""
                select line
                from PropertyCreditNoteLine line
                join line.creditNote note
                join note.invoice invoice
                where line.hotel.id = :propertyId
                  and note.issuedAt >= :fromInclusive
                  and note.issuedAt < :toExclusive
                  and invoice.status in :invoiceStatuses
                """);
        appendInvoicePaymentFilters(jpql, "invoice", scope);
        appendRoomTypeFilter(jpql, "invoice.reservation", scope);
        jpql.append(" order by note.issuedAt, note.id, line.id");

        TypedQuery<PropertyCreditNoteLine> query = entityManager.createQuery(
                jpql.toString(), PropertyCreditNoteLine.class);
        bindBase(query, scope);
        bindInvoiceStatuses(query);
        bindPaymentFilters(query, scope);
        bindRoomType(query, scope);
        return query.getResultList().stream().map(line -> new CreditNoteLineSource(
                line.getId(),
                line.getCreditNote().getId(),
                line.getInvoiceLine() == null ? null : line.getInvoiceLine().getId(),
                line.getDescription(),
                line.getAmount())).toList();
    }

    private void appendDirectPaymentFilters(StringBuilder jpql, String transactionAlias, QueryScope scope) {
        if (scope.filters().provider() != null) {
            jpql.append(" and ").append(transactionAlias).append(".provider = :provider");
        }
        if (scope.filters().method() != null) {
            jpql.append(" and ").append(transactionAlias).append(".method = :method");
        }
        if (scope.transactionType() != null) {
            jpql.append(" and ").append(transactionAlias).append(".transactionType = :transactionType");
        }
    }

    private void appendInvoicePaymentFilters(StringBuilder jpql, String invoiceAlias, QueryScope scope) {
        if (!scope.hasPaymentFilters()) {
            return;
        }
        jpql.append(" and exists (select paymentAllocation.id from PropertyInvoicePaymentAllocation paymentAllocation")
                .append(" where paymentAllocation.invoice = ").append(invoiceAlias);
        appendDirectPaymentFilters(jpql, "paymentAllocation.financialTransaction", scope);
        jpql.append(')');
    }

    private void appendRoomTypeFilter(StringBuilder jpql, String reservationPath, QueryScope scope) {
        if (scope.filters().roomType() == null) {
            return;
        }
        jpql.append(" and (exists (select detail.id from ReservationDetail detail")
                .append(" left join detail.roomType requestedRoomType")
                .append(" left join detail.room assignedRoom")
                .append(" left join assignedRoom.roomType assignedRoomType")
                .append(" where detail.reservation = ").append(reservationPath)
                .append(" and (upper(requestedRoomType.code) = :roomType")
                .append(" or upper(assignedRoomType.code) = :roomType))")
                .append(" or exists (select fallbackRoom.id from Room fallbackRoom")
                .append(" where fallbackRoom = ").append(reservationPath).append(".room")
                .append(" and upper(fallbackRoom.roomType.code) = :roomType))");
    }

    private void bindBase(Query query, QueryScope scope) {
        query.setParameter("propertyId", scope.filters().propertyId());
        query.setParameter("fromInclusive", scope.fromInclusive());
        query.setParameter("toExclusive", scope.toExclusive());
    }

    private void bindInvoiceStatuses(Query query) {
        query.setParameter("invoiceStatuses", List.of(
                PropertyInvoice.Status.FINALIZED,
                PropertyInvoice.Status.CREDITED));
    }

    private void bindPaymentFilters(Query query, QueryScope scope) {
        if (scope.filters().provider() != null) {
            query.setParameter("provider", scope.filters().provider());
        }
        if (scope.filters().method() != null) {
            query.setParameter("method", scope.filters().method());
        }
        if (scope.transactionType() != null) {
            query.setParameter("transactionType", scope.transactionType());
        }
    }

    private void bindRoomType(Query query, QueryScope scope) {
        if (scope.filters().roomType() != null) {
            query.setParameter("roomType", scope.filters().roomType());
        }
    }

    public record PropertyRevenueSource(
            List<TransactionSource> transactions,
            List<InvoiceSource> invoices,
            List<InvoiceLineSource> invoiceLines,
            List<AllocationSource> allocations,
            List<CreditNoteSource> creditNotes,
            List<CreditNoteLineSource> creditNoteLines) {

        public PropertyRevenueSource {
            transactions = List.copyOf(transactions);
            invoices = List.copyOf(invoices);
            invoiceLines = List.copyOf(invoiceLines);
            allocations = List.copyOf(allocations);
            creditNotes = List.copyOf(creditNotes);
            creditNoteLines = List.copyOf(creditNoteLines);
        }
    }

    public record TransactionSource(
            String publicId,
            LocalDateTime occurredAt,
            PropertyFinancialTransaction.TransactionType transactionType,
            PropertyFinancialTransaction.Direction direction,
            BigDecimal amount,
            String method,
            String provider,
            Long reservationId,
            Long invoiceId,
            String originalTransactionPublicId) {
    }

    public record InvoiceSource(
            Long id,
            String invoiceNumber,
            Long reservationId,
            LocalDateTime finalizedAt,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal feeAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal balanceAmount) {
    }

    public record InvoiceLineSource(
            Long id,
            Long invoiceId,
            PropertyInvoiceLine.LineType lineType,
            String code,
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            BigDecimal economicEffect) {
    }

    public record AllocationSource(
            Long id,
            Long invoiceId,
            String transactionPublicId,
            PropertyFinancialTransaction.TransactionType transactionType,
            BigDecimal transactionAmount,
            LocalDateTime transactionOccurredAt,
            BigDecimal allocatedAmount) {
    }

    public record CreditNoteSource(
            Long id,
            String creditNoteNumber,
            Long invoiceId,
            LocalDateTime issuedAt,
            BigDecimal amount) {
    }

    public record CreditNoteLineSource(
            Long id,
            Long creditNoteId,
            Long invoiceLineId,
            String description,
            BigDecimal amount) {
    }

    private record QueryScope(
            NormalizedFilters filters,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            PropertyFinancialTransaction.TransactionType transactionType) {

        private static QueryScope from(NormalizedFilters filters) {
            Objects.requireNonNull(filters, "filters must not be null");
            if (filters.context() != FinancialContext.PROPERTY_COMMERCE) {
                throw new IllegalArgumentException("Property revenue queries require Property Commerce filters.");
            }
            if (filters.planCode() != null) {
                throw new IllegalArgumentException("Property revenue queries cannot use a platform plan filter.");
            }
            PropertyFinancialTransaction.TransactionType transactionType = null;
            if (filters.transactionType() != null) {
                try {
                    transactionType = PropertyFinancialTransaction.TransactionType.valueOf(
                            filters.transactionType());
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unsupported Property Commerce transaction type.", exception);
                }
            }
            return new QueryScope(
                    filters,
                    LocalDateTime.ofInstant(filters.fromInclusive(), ZoneOffset.UTC),
                    LocalDateTime.ofInstant(filters.toExclusive(), ZoneOffset.UTC),
                    transactionType);
        }

        private boolean hasPaymentFilters() {
            return filters.provider() != null || filters.method() != null || transactionType != null;
        }
    }
}

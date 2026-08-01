package com.hotel.propertycommerce.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.BreakdownDimension;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationIssue;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationStatus;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReportTotals;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueBreakdown;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueTransactionRow;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.propertycommerce.invoice.PropertyInvoiceLine;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.AllocationSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.CreditNoteLineSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.CreditNoteSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.InvoiceLineSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.InvoiceSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.PropertyRevenueSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.TransactionSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Calculates one-VND property report totals without re-counting invoice allocations. */
@Service
@Transactional(readOnly = true)
public class PropertyRevenueService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0);

    private final PropertyRevenueRepository repository;
    private final Clock clock;

    @Autowired
    public PropertyRevenueService(PropertyRevenueRepository repository) {
        this(repository, Clock.systemUTC());
    }

    PropertyRevenueService(PropertyRevenueRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public RevenueReportResult generate(NormalizedFilters filters) {
        requirePropertyFilters(filters);
        PropertyRevenueSource source = repository.load(filters);
        Reconciliation reconciliation = reconcile(source);

        BigDecimal cashGross = source.transactions().stream()
                .filter(transaction -> transaction.direction() == PropertyFinancialTransaction.Direction.DEBIT)
                .map(TransactionSource::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal cashRefunds = source.transactions().stream()
                .filter(transaction -> transaction.direction() == PropertyFinancialTransaction.Direction.CREDIT)
                .map(TransactionSource::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal cashNet = cashGross.subtract(cashRefunds);

        BigDecimal invoiceGross = source.invoices().stream()
                .map(InvoiceSource::totalAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal invoiceCredits = source.creditNotes().stream()
                .map(CreditNoteSource::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal invoiceNet = invoiceGross.subtract(invoiceCredits);
        BigDecimal unpaidBalance = adjustedUnpaidBalance(source);
        BigDecimal heldDeposits = heldDeposits(source);

        boolean invoicedBasis = filters.basis() == RecognitionBasis.INVOICED;
        BigDecimal primaryGross = invoicedBasis ? invoiceGross : cashGross;
        BigDecimal primaryRefunds = invoicedBasis ? ZERO : cashRefunds;
        BigDecimal primaryCredits = invoicedBasis ? invoiceCredits : ZERO;
        BigDecimal primaryNet = primaryGross.subtract(primaryRefunds).subtract(primaryCredits);

        ReportTotals totals = new ReportTotals(
                primaryGross,
                primaryRefunds,
                primaryCredits,
                primaryNet,
                cashGross,
                invoiceNet,
                unpaidBalance,
                heldDeposits,
                source.transactions().size(),
                0,
                reconciliation.unreconciledSourceCount());

        List<RevenueTransactionRow> rows = invoicedBasis
                ? invoiceRows(filters, source, reconciliation)
                : transactionRows(filters, source, reconciliation);
        return new RevenueReportResult(
                filters,
                totals,
                breakdowns(source),
                rows,
                reconciliation.issues(),
                rows.size(),
                sourceWatermark(filters, source),
                null,
                clock.instant());
    }

    private Reconciliation reconcile(PropertyRevenueSource source) {
        List<ReconciliationIssue> issues = new ArrayList<>();
        Set<String> mismatchedTransactions = new HashSet<>();
        Set<Long> mismatchedInvoices = new HashSet<>();

        Map<String, BigDecimal> transactionAmounts = new HashMap<>();
        source.transactions().forEach(transaction -> transactionAmounts.put(
                transaction.publicId(), transaction.amount()));
        source.allocations().forEach(allocation -> transactionAmounts.putIfAbsent(
                allocation.transactionPublicId(), allocation.transactionAmount()));

        Map<String, BigDecimal> allocatedByTransaction = sumAllocationsByTransaction(source.allocations());
        allocatedByTransaction.forEach((transactionId, allocated) -> {
            BigDecimal transactionAmount = transactionAmounts.get(transactionId);
            if (transactionAmount == null) {
                issues.add(issue(
                        "ALLOCATION_TRANSACTION_MISSING", "TRANSACTION", transactionId,
                        ZERO, allocated, "Invoice allocation has no authoritative transaction evidence."));
                mismatchedTransactions.add(transactionId);
            } else if (allocated.compareTo(transactionAmount) > 0) {
                issues.add(issue(
                        "ALLOCATION_EXCEEDS_TRANSACTION", "TRANSACTION", transactionId,
                        transactionAmount, allocated,
                        "Cumulative invoice allocation exceeds the immutable transaction amount."));
                mismatchedTransactions.add(transactionId);
            }
        });

        Map<Long, BigDecimal> allocatedByInvoice = sumAllocationsByInvoice(source.allocations());
        for (InvoiceSource invoice : source.invoices()) {
            BigDecimal allocated = allocatedByInvoice.getOrDefault(invoice.id(), ZERO);
            if (allocated.compareTo(invoice.paidAmount()) != 0) {
                issues.add(issue(
                        "INVOICE_ALLOCATION_MISMATCH", "INVOICE", invoice.id().toString(),
                        invoice.paidAmount(), allocated,
                        "Invoice paid amount does not match immutable payment allocations."));
                mismatchedInvoices.add(invoice.id());
            }
        }

        Map<Long, BigDecimal> lineEffectsByInvoice = new HashMap<>();
        source.invoiceLines().forEach(line -> lineEffectsByInvoice.merge(
                line.invoiceId(), line.economicEffect(), BigDecimal::add));
        for (InvoiceSource invoice : source.invoices()) {
            BigDecimal lineTotal = lineEffectsByInvoice.getOrDefault(invoice.id(), ZERO);
            if (lineTotal.compareTo(invoice.totalAmount()) != 0) {
                issues.add(issue(
                        "INVOICE_LINE_TOTAL_MISMATCH", "INVOICE", invoice.id().toString(),
                        invoice.totalAmount(), lineTotal,
                        "Finalized invoice header does not reconcile with immutable invoice lines."));
                mismatchedInvoices.add(invoice.id());
            }
        }

        Map<Long, BigDecimal> creditLinesByNote = new HashMap<>();
        source.creditNoteLines().forEach(line -> creditLinesByNote.merge(
                line.creditNoteId(), line.amount(), BigDecimal::add));
        for (CreditNoteSource note : source.creditNotes()) {
            BigDecimal lineTotal = creditLinesByNote.getOrDefault(note.id(), ZERO);
            if (lineTotal.compareTo(note.amount()) != 0) {
                issues.add(issue(
                        "CREDIT_NOTE_LINE_MISMATCH", "INVOICE", note.invoiceId().toString(),
                        note.amount(), lineTotal,
                        "Credit-note header does not reconcile with its immutable lines."));
                mismatchedInvoices.add(note.invoiceId());
            }
        }

        long sourceCount = issues.stream()
                .map(issue -> issue.sourceType() + ':' + issue.sourceId())
                .distinct()
                .count();
        return new Reconciliation(
                List.copyOf(issues),
                Set.copyOf(mismatchedTransactions),
                Set.copyOf(mismatchedInvoices),
                sourceCount);
    }

    private BigDecimal adjustedUnpaidBalance(PropertyRevenueSource source) {
        Map<Long, BigDecimal> creditsByInvoice = new HashMap<>();
        source.creditNotes().forEach(note -> creditsByInvoice.merge(
                note.invoiceId(), note.amount(), BigDecimal::add));
        return source.invoices().stream()
                .map(invoice -> invoice.balanceAmount()
                        .subtract(creditsByInvoice.getOrDefault(invoice.id(), ZERO))
                        .max(ZERO))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal heldDeposits(PropertyRevenueSource source) {
        Map<String, BigDecimal> allocated = sumAllocationsByTransaction(source.allocations());
        Map<String, BigDecimal> refunded = new HashMap<>();
        source.transactions().stream()
                .filter(transaction -> transaction.direction() == PropertyFinancialTransaction.Direction.CREDIT)
                .filter(transaction -> transaction.originalTransactionPublicId() != null)
                .forEach(transaction -> refunded.merge(
                        transaction.originalTransactionPublicId(), transaction.amount(), BigDecimal::add));
        return source.transactions().stream()
                .filter(transaction -> transaction.direction() == PropertyFinancialTransaction.Direction.DEBIT)
                .filter(transaction -> transaction.transactionType()
                        == PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT)
                .map(transaction -> transaction.amount()
                        .subtract(allocated.getOrDefault(transaction.publicId(), ZERO))
                        .subtract(refunded.getOrDefault(transaction.publicId(), ZERO))
                        .max(ZERO))
                .reduce(ZERO, BigDecimal::add);
    }

    private List<RevenueTransactionRow> transactionRows(
            NormalizedFilters filters,
            PropertyRevenueSource source,
            Reconciliation reconciliation) {
        return source.transactions().stream().map(transaction -> {
            boolean debit = transaction.direction() == PropertyFinancialTransaction.Direction.DEBIT;
            BigDecimal gross = debit ? transaction.amount() : ZERO;
            BigDecimal refund = debit ? ZERO : transaction.amount();
            Map<String, String> dimensions = new LinkedHashMap<>();
            put(dimensions, "reservationId", transaction.reservationId());
            put(dimensions, "invoiceId", transaction.invoiceId());
            put(dimensions, "originalTransactionId", transaction.originalTransactionPublicId());
            return new RevenueTransactionRow(
                    FinancialContext.PROPERTY_COMMERCE,
                    transaction.publicId(),
                    utc(transaction.occurredAt()),
                    transaction.transactionType().name(),
                    "TRANSACTION",
                    transaction.publicId(),
                    filters.propertyId(),
                    transaction.method(),
                    transaction.provider(),
                    gross,
                    refund,
                    ZERO,
                    gross.subtract(refund),
                    dimensions,
                    reconciliation.mismatchedTransactions().contains(transaction.publicId())
                            ? ReconciliationStatus.MISMATCH
                            : ReconciliationStatus.RECONCILED);
        }).toList();
    }

    private List<RevenueTransactionRow> invoiceRows(
            NormalizedFilters filters,
            PropertyRevenueSource source,
            Reconciliation reconciliation) {
        Map<Long, BigDecimal> creditsByInvoice = new HashMap<>();
        source.creditNotes().forEach(note -> creditsByInvoice.merge(
                note.invoiceId(), note.amount(), BigDecimal::add));
        Set<Long> includedInvoices = new HashSet<>();
        List<RevenueTransactionRow> rows = new ArrayList<>();
        source.invoices().forEach(invoice -> {
            includedInvoices.add(invoice.id());
            BigDecimal credits = creditsByInvoice.getOrDefault(invoice.id(), ZERO);
            rows.add(new RevenueTransactionRow(
                    FinancialContext.PROPERTY_COMMERCE,
                    invoice.invoiceNumber(),
                    utc(invoice.finalizedAt()),
                    "INVOICE",
                    "INVOICE",
                    invoice.id().toString(),
                    filters.propertyId(),
                    null,
                    null,
                    invoice.totalAmount(),
                    ZERO,
                    credits,
                    invoice.totalAmount().subtract(credits),
                    Map.of("RESERVATION_ID", invoice.reservationId().toString()),
                    reconciliation.mismatchedInvoices().contains(invoice.id())
                            ? ReconciliationStatus.MISMATCH
                            : ReconciliationStatus.RECONCILED));
        });
        source.creditNotes().stream()
                .filter(note -> !includedInvoices.contains(note.invoiceId()))
                .forEach(note -> rows.add(new RevenueTransactionRow(
                        FinancialContext.PROPERTY_COMMERCE,
                        note.creditNoteNumber(),
                        utc(note.issuedAt()),
                        "CREDIT_NOTE",
                        "CREDIT_NOTE",
                        note.id().toString(),
                        filters.propertyId(),
                        null,
                        null,
                        ZERO,
                        ZERO,
                        note.amount(),
                        note.amount().negate(),
                        Map.of("INVOICE_ID", note.invoiceId().toString()),
                        reconciliation.mismatchedInvoices().contains(note.invoiceId())
                                ? ReconciliationStatus.MISMATCH
                                : ReconciliationStatus.RECONCILED)));
        rows.sort(Comparator.comparing(RevenueTransactionRow::occurredAt)
                .thenComparing(RevenueTransactionRow::publicId));
        return List.copyOf(rows);
    }

    private List<RevenueBreakdown> breakdowns(PropertyRevenueSource source) {
        Map<BreakdownKey, MutableBreakdown> groups = new HashMap<>();
        for (TransactionSource transaction : source.transactions()) {
            addTransactionBreakdown(groups, BreakdownDimension.TRANSACTION_TYPE,
                    transaction.transactionType().name(), transaction.transactionType().name(), transaction);
            if (transaction.method() != null) {
                addTransactionBreakdown(groups, BreakdownDimension.PAYMENT_METHOD,
                        transaction.method(), transaction.method(), transaction);
            }
            if (transaction.provider() != null) {
                addTransactionBreakdown(groups, BreakdownDimension.PROVIDER,
                        transaction.provider(), transaction.provider(), transaction);
            }
        }

        Map<Long, BigDecimal> creditsByInvoiceLine = new HashMap<>();
        source.creditNoteLines().stream()
                .filter(line -> line.invoiceLineId() != null)
                .forEach(line -> creditsByInvoiceLine.merge(
                        line.invoiceLineId(), line.amount(), BigDecimal::add));
        for (InvoiceLineSource line : source.invoiceLines()) {
            addInvoiceBreakdown(groups, BreakdownDimension.TRANSACTION_TYPE,
                    line.lineType().name(), line.lineType().name(), line, creditsByInvoiceLine);
            if (line.lineType() == PropertyInvoiceLine.LineType.ROOM) {
                addInvoiceBreakdown(groups, BreakdownDimension.ROOM_TYPE,
                        code(line), line.name(), line, creditsByInvoiceLine);
            }
            if (line.lineType() == PropertyInvoiceLine.LineType.SERVICE
                    || line.lineType() == PropertyInvoiceLine.LineType.MINIBAR) {
                addInvoiceBreakdown(groups, BreakdownDimension.SERVICE,
                        code(line), line.name(), line, creditsByInvoiceLine);
            }
        }
        return groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing((BreakdownKey key) -> key.dimension().name())
                        .thenComparing(BreakdownKey::code)))
                .map(entry -> entry.getValue().toResult(entry.getKey()))
                .toList();
    }

    private void addTransactionBreakdown(
            Map<BreakdownKey, MutableBreakdown> groups,
            BreakdownDimension dimension,
            String code,
            String label,
            TransactionSource transaction) {
        MutableBreakdown group = groups.computeIfAbsent(
                new BreakdownKey(dimension, code, label), ignored -> new MutableBreakdown());
        group.count++;
        if (transaction.direction() == PropertyFinancialTransaction.Direction.DEBIT) {
            group.gross = group.gross.add(transaction.amount());
        } else {
            group.refunds = group.refunds.add(transaction.amount());
        }
    }

    private void addInvoiceBreakdown(
            Map<BreakdownKey, MutableBreakdown> groups,
            BreakdownDimension dimension,
            String code,
            String label,
            InvoiceLineSource line,
            Map<Long, BigDecimal> creditsByInvoiceLine) {
        MutableBreakdown group = groups.computeIfAbsent(
                new BreakdownKey(dimension, code, label), ignored -> new MutableBreakdown());
        group.count++;
        if (line.economicEffect().signum() >= 0) {
            group.gross = group.gross.add(line.economicEffect());
        } else {
            group.credits = group.credits.add(line.economicEffect().abs());
        }
        group.credits = group.credits.add(creditsByInvoiceLine.getOrDefault(line.id(), ZERO));
    }

    private Map<String, BigDecimal> sumAllocationsByTransaction(List<AllocationSource> allocations) {
        Map<String, BigDecimal> totals = new HashMap<>();
        allocations.forEach(allocation -> totals.merge(
                allocation.transactionPublicId(), allocation.allocatedAmount(), BigDecimal::add));
        return totals;
    }

    private Map<Long, BigDecimal> sumAllocationsByInvoice(List<AllocationSource> allocations) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        allocations.forEach(allocation -> totals.merge(
                allocation.invoiceId(), allocation.allocatedAmount(), BigDecimal::add));
        return totals;
    }

    private ReconciliationIssue issue(
            String code,
            String sourceType,
            String sourceId,
            BigDecimal expected,
            BigDecimal actual,
            String message) {
        return new ReconciliationIssue(
                code, sourceType, sourceId, expected, actual, actual.subtract(expected), message);
    }

    private String sourceWatermark(NormalizedFilters filters, PropertyRevenueSource source) {
        Instant latest = source.transactions().stream().map(TransactionSource::occurredAt).map(this::utc)
                .max(Instant::compareTo).orElse(null);
        latest = later(latest, source.invoices().stream().map(InvoiceSource::finalizedAt)
                .map(this::utc).max(Instant::compareTo).orElse(null));
        latest = later(latest, source.creditNotes().stream().map(CreditNoteSource::issuedAt)
                .map(this::utc).max(Instant::compareTo).orElse(null));
        String evidence = latest == null ? "EMPTY" : latest.toString();
        return "PROPERTY:" + filters.propertyId() + ':' + evidence;
    }

    private Instant later(Instant left, Instant right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private Instant utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private String code(InvoiceLineSource line) {
        return line.code() == null || line.code().isBlank()
                ? line.lineType().name() + ':' + line.id()
                : line.code();
    }

    private void put(Map<String, String> dimensions, String key, Object value) {
        if (value != null) {
            dimensions.put(key, value.toString());
        }
    }

    private void requirePropertyFilters(NormalizedFilters filters) {
        Objects.requireNonNull(filters, "filters must not be null");
        if (filters.context() != FinancialContext.PROPERTY_COMMERCE) {
            throw new IllegalArgumentException("Property revenue reports require Property Commerce filters.");
        }
    }

    private record BreakdownKey(BreakdownDimension dimension, String code, String label) {
    }

    private static final class MutableBreakdown {
        private long count;
        private BigDecimal gross = ZERO;
        private BigDecimal refunds = ZERO;
        private BigDecimal credits = ZERO;

        private RevenueBreakdown toResult(BreakdownKey key) {
            return new RevenueBreakdown(
                    key.dimension(),
                    key.code(),
                    key.label(),
                    count,
                    gross,
                    refunds,
                    credits,
                    gross.subtract(refunds).subtract(credits),
                    false);
        }
    }

    private record Reconciliation(
            List<ReconciliationIssue> issues,
            Set<String> mismatchedTransactions,
            Set<Long> mismatchedInvoices,
            long unreconciledSourceCount) {
    }
}

package com.hotel.platformbilling.reporting;

import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
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
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.reporting.PlatformRevenueRepository.AttemptSource;
import com.hotel.platformbilling.reporting.PlatformRevenueRepository.EntitlementSource;
import com.hotel.platformbilling.reporting.PlatformRevenueRepository.OrderSource;
import com.hotel.platformbilling.reporting.PlatformRevenueRepository.PlatformRevenueSource;
import com.hotel.platformbilling.reporting.PlatformRevenueRepository.TransactionSource;
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

/** Calculates system-scoped SaaS revenue from immutable Platform Billing evidence. */
@Service
@Transactional(readOnly = true)
public class PlatformRevenueService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0);

    private final PlatformRevenueRepository repository;
    private final Clock clock;

    public PlatformRevenueService(PlatformRevenueRepository repository) {
        this(repository, Clock.systemUTC());
    }

    PlatformRevenueService(PlatformRevenueRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public RevenueReportResult generate(NormalizedFilters filters) {
        requirePlatformFilters(filters);
        PlatformRevenueSource source = repository.load(filters);
        Reconciliation reconciliation = reconcile(source);

        BigDecimal gross = source.transactions().stream()
                .filter(transaction -> transaction.direction() == PlatformFinancialTransaction.Direction.DEBIT)
                .map(TransactionSource::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal refunds = source.transactions().stream()
                .filter(transaction -> transaction.transactionType()
                        == PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_REFUND)
                .map(TransactionSource::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal credits = source.transactions().stream()
                .filter(transaction -> transaction.transactionType()
                        == PlatformFinancialTransaction.TransactionType.DOWNGRADE_CREDIT)
                .map(TransactionSource::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal net = gross.subtract(refunds).subtract(credits);

        BigDecimal unpaid = source.orders().stream()
                .filter(order -> order.status() == SubscriptionOrderState.CREATED
                        || order.status() == SubscriptionOrderState.PENDING_PAYMENT)
                .map(OrderSource::price)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal primaryGross = gross;
        BigDecimal primaryRefunds = refunds;
        BigDecimal primaryCredits = credits;
        if (filters.basis() == RecognitionBasis.INVOICED) {
            // Platform Billing has no property invoice aggregate; successful ledger debits are
            // the finalized invoiced snapshot, while credits/refunds remain separate corrections.
            primaryGross = gross;
            primaryRefunds = refunds;
            primaryCredits = credits;
        }

        ReportTotals totals = new ReportTotals(
                primaryGross,
                primaryRefunds,
                primaryCredits,
                primaryGross.subtract(primaryRefunds).subtract(primaryCredits),
                gross,
                net,
                unpaid,
                ZERO,
                source.transactions().stream()
                        .filter(transaction -> transaction.direction() == PlatformFinancialTransaction.Direction.DEBIT)
                        .count(),
                source.attempts().stream()
                        .filter(attempt -> attempt.status() == PlatformPaymentAttempt.Status.FAILED)
                        .count(),
                reconciliation.unreconciledSourceCount());

        List<RevenueTransactionRow> rows = rows(filters, source, reconciliation);
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

    private Reconciliation reconcile(PlatformRevenueSource source) {
        List<ReconciliationIssue> issues = new ArrayList<>();
        Set<String> mismatchedTransactions = new HashSet<>();
        Map<Long, List<TransactionSource>> transactionsByAttempt = new HashMap<>();
        for (TransactionSource transaction : source.transactions()) {
            if (transaction.attemptId() != null) {
                transactionsByAttempt.computeIfAbsent(transaction.attemptId(), ignored -> new ArrayList<>())
                        .add(transaction);
            }
        }
        for (AttemptSource attempt : source.attempts()) {
            if (attempt.status() != PlatformPaymentAttempt.Status.SUCCESS) {
                continue;
            }
            List<TransactionSource> linked = transactionsByAttempt.getOrDefault(attempt.id(), List.of());
            TransactionSource debit = linked.stream()
                    .filter(transaction -> transaction.direction() == PlatformFinancialTransaction.Direction.DEBIT)
                    .findFirst().orElse(null);
            if (debit == null) {
                issues.add(issue(
                        "PLATFORM_PAYMENT_LEDGER_MISSING", "PAYMENT_ATTEMPT", attempt.publicId(),
                        attempt.expectedAmount(), ZERO,
                        "Successful platform payment attempt has no immutable debit transaction."));
                mismatchedTransactions.add(attempt.publicId());
            } else if (debit.amount().compareTo(attempt.expectedAmount()) != 0) {
                issues.add(issue(
                        "PLATFORM_PAYMENT_AMOUNT_MISMATCH", "PAYMENT_ATTEMPT", attempt.publicId(),
                        attempt.expectedAmount(), debit.amount(),
                        "Ledger debit does not match the server-owned payment attempt amount."));
                mismatchedTransactions.add(debit.publicId());
            }
        }
        long sourceCount = issues.stream()
                .map(issue -> issue.sourceType() + ':' + issue.sourceId())
                .distinct().count();
        return new Reconciliation(List.copyOf(issues), Set.copyOf(mismatchedTransactions), sourceCount);
    }

    private List<RevenueTransactionRow> rows(
            NormalizedFilters filters,
            PlatformRevenueSource source,
            Reconciliation reconciliation) {
        List<RevenueTransactionRow> rows = source.transactions().stream().map(transaction -> {
            boolean debit = transaction.direction() == PlatformFinancialTransaction.Direction.DEBIT;
            boolean refund = transaction.transactionType()
                    == PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_REFUND;
            boolean credit = transaction.transactionType()
                    == PlatformFinancialTransaction.TransactionType.DOWNGRADE_CREDIT;
            BigDecimal gross = debit ? transaction.amount() : ZERO;
            BigDecimal refundAmount = refund ? transaction.amount() : ZERO;
            BigDecimal creditAmount = credit ? transaction.amount() : ZERO;
            Map<String, String> dimensions = new LinkedHashMap<>();
            put(dimensions, "ORDER_ID", transaction.orderPublicId());
            put(dimensions, "ORDER_CODE", transaction.orderCode());
            put(dimensions, "PLAN_CODE", transaction.planCode());
            put(dimensions, "PLAN_NAME", transaction.planName());
            put(dimensions, "BILLING_PERIOD", transaction.billingPeriod());
            put(dimensions, "OPERATION", transaction.operation());
            put(dimensions, "ORDER_STATUS", transaction.orderStatus());
            put(dimensions, "ORIGINAL_TRANSACTION_ID", transaction.originalTransactionPublicId());
            return new RevenueTransactionRow(
                    FinancialContext.PLATFORM_BILLING,
                    transaction.publicId(),
                    utc(transaction.occurredAt()),
                    transaction.transactionType().name(),
                    "PLATFORM_TRANSACTION",
                    transaction.publicId(),
                    null,
                    transaction.method(),
                    transaction.provider(),
                    gross,
                    refundAmount,
                    creditAmount,
                    gross.subtract(refundAmount).subtract(creditAmount),
                    dimensions,
                    reconciliation.mismatchedTransactions().contains(transaction.publicId())
                            ? ReconciliationStatus.MISMATCH : ReconciliationStatus.RECONCILED);
        }).sorted(Comparator.comparing(RevenueTransactionRow::occurredAt)
                .thenComparing(RevenueTransactionRow::publicId)).toList();
        return List.copyOf(rows);
    }

    private List<RevenueBreakdown> breakdowns(PlatformRevenueSource source) {
        Map<BreakdownKey, MutableBreakdown> groups = new HashMap<>();
        for (TransactionSource transaction : source.transactions()) {
            addTransaction(groups, BreakdownDimension.TRANSACTION_TYPE,
                    transaction.transactionType().name(), transaction.transactionType().name(), transaction);
            if (transaction.method() != null) {
                addTransaction(groups, BreakdownDimension.PAYMENT_METHOD,
                        transaction.method(), transaction.method(), transaction);
            }
            if (transaction.provider() != null) {
                addTransaction(groups, BreakdownDimension.PROVIDER,
                        transaction.provider(), transaction.provider(), transaction);
            }
            if (transaction.planCode() != null) {
                addTransaction(groups, BreakdownDimension.PLAN,
                        transaction.planCode(), transaction.planName(), transaction);
                MutableBreakdown plan = groups.get(new BreakdownKey(
                        BreakdownDimension.PLAN, transaction.planCode(), transaction.planName()));
                plan.recurringEligible = transaction.planCode() != null
                        && transaction.billingPeriod() != null
                        && !"ONCE".equalsIgnoreCase(transaction.billingPeriod())
                        && !"LIFETIME".equalsIgnoreCase(transaction.billingPeriod());
            }
        }
        for (AttemptSource attempt : source.attempts()) {
            BreakdownKey key = new BreakdownKey(
                    BreakdownDimension.PROVIDER_STATUS, attempt.status().name(), attempt.status().name());
            MutableBreakdown group = groups.computeIfAbsent(key, ignored -> new MutableBreakdown());
            group.count++;
            if (attempt.status() == PlatformPaymentAttempt.Status.SUCCESS) {
                group.gross = group.gross.add(attempt.expectedAmount());
            }
        }
        for (EntitlementSource entitlement : source.entitlements()) {
            BreakdownKey key = new BreakdownKey(
                    BreakdownDimension.SUBSCRIPTION_STATUS, entitlement.status().name(), entitlement.status().name());
            MutableBreakdown group = groups.computeIfAbsent(key, ignored -> new MutableBreakdown());
            group.count++;
        }
        if (source.entitlements().isEmpty()) {
            for (OrderSource order : source.orders()) {
                BreakdownKey key = new BreakdownKey(
                        BreakdownDimension.SUBSCRIPTION_STATUS, order.status().name(), order.status().name());
                MutableBreakdown group = groups.computeIfAbsent(key, ignored -> new MutableBreakdown());
                group.count++;
            }
        }
        return groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing((BreakdownKey key) -> key.dimension().name())
                        .thenComparing(BreakdownKey::code)))
                .map(entry -> entry.getValue().toResult(entry.getKey()))
                .toList();
    }

    private void addTransaction(
            Map<BreakdownKey, MutableBreakdown> groups,
            BreakdownDimension dimension,
            String code,
            String label,
            TransactionSource transaction) {
        BreakdownKey key = new BreakdownKey(dimension, code, label);
        MutableBreakdown group = groups.computeIfAbsent(key, ignored -> new MutableBreakdown());
        group.count++;
        if (transaction.direction() == PlatformFinancialTransaction.Direction.DEBIT) {
            group.gross = group.gross.add(transaction.amount());
        } else if (transaction.transactionType() == PlatformFinancialTransaction.TransactionType.DOWNGRADE_CREDIT) {
            group.credits = group.credits.add(transaction.amount());
        } else {
            group.refunds = group.refunds.add(transaction.amount());
        }
    }

    private ReconciliationIssue issue(
            String code, String sourceType, String sourceId,
            BigDecimal expected, BigDecimal actual, String message) {
        return new ReconciliationIssue(code, sourceType, sourceId,
                expected, actual, actual.subtract(expected), message);
    }

    private String sourceWatermark(NormalizedFilters filters, PlatformRevenueSource source) {
        Instant latest = source.transactions().stream().map(TransactionSource::occurredAt).map(this::utc)
                .max(Instant::compareTo).orElse(null);
        latest = later(latest, source.attempts().stream().map(AttemptSource::createdAt).map(this::utc)
                .max(Instant::compareTo).orElse(null));
        latest = later(latest, source.orders().stream().map(OrderSource::createdAt).map(this::utc)
                .max(Instant::compareTo).orElse(null));
        String evidence = latest == null ? "EMPTY" : latest.toString();
        return "PLATFORM:" + evidence;
    }

    private Instant later(Instant left, Instant right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private Instant utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private void put(Map<String, String> dimensions, String key, Object value) {
        if (value != null) {
            dimensions.put(key, value.toString());
        }
    }

    private void requirePlatformFilters(NormalizedFilters filters) {
        Objects.requireNonNull(filters, "filters must not be null");
        if (filters.context() != FinancialContext.PLATFORM_BILLING || filters.propertyId() != null
                || filters.roomType() != null) {
            throw new IllegalArgumentException("Platform revenue reports require system-scoped Platform Billing filters.");
        }
    }

    private record BreakdownKey(BreakdownDimension dimension, String code, String label) {
    }

    private static final class MutableBreakdown {
        private long count;
        private BigDecimal gross = ZERO;
        private BigDecimal refunds = ZERO;
        private BigDecimal credits = ZERO;
        private boolean recurringEligible;

        private RevenueBreakdown toResult(BreakdownKey key) {
            return new RevenueBreakdown(key.dimension(), key.code(), key.label(), count,
                    gross, refunds, credits, gross.subtract(refunds).subtract(credits), recurringEligible);
        }
    }

    private record Reconciliation(
            List<ReconciliationIssue> issues,
            Set<String> mismatchedTransactions,
            long unreconciledSourceCount) {
    }
}

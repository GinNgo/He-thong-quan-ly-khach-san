package com.hotel.paymentprovider.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Shared, context-safe models used by report APIs, exports and reconciliation. */
public final class RevenueReportModels {

    private RevenueReportModels() {
    }

    public enum FinancialContext {
        PROPERTY_COMMERCE,
        PLATFORM_BILLING
    }

    public enum RecognitionBasis {
        CASH_COLLECTED,
        INVOICED,
        NET
    }

    public enum BreakdownDimension {
        TRANSACTION_TYPE,
        PAYMENT_METHOD,
        PROVIDER,
        ROOM_TYPE,
        SERVICE,
        PLAN,
        SUBSCRIPTION_STATUS,
        PROVIDER_STATUS
    }

    public enum ReconciliationStatus {
        RECONCILED,
        UNRECONCILED,
        MISMATCH
    }

    public record NormalizedFilters(
            FinancialContext context,
            RecognitionBasis basis,
            Instant fromInclusive,
            Instant toExclusive,
            String zoneId,
            Long propertyId,
            String provider,
            String method,
            String transactionType,
            String roomType,
            String planCode) {

        public NormalizedFilters {
            context = Objects.requireNonNull(context, "context must not be null");
            basis = Objects.requireNonNull(basis, "basis must not be null");
            fromInclusive = Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
            toExclusive = Objects.requireNonNull(toExclusive, "toExclusive must not be null");
            if (!fromInclusive.isBefore(toExclusive)) {
                throw new IllegalArgumentException("fromInclusive must be before toExclusive.");
            }
            zoneId = ZoneId.of(requireText(zoneId, "zoneId", 64)).getId();
            if (context == FinancialContext.PROPERTY_COMMERCE) {
                if (propertyId == null || propertyId <= 0) {
                    throw new IllegalArgumentException("Property Commerce reports require a positive propertyId.");
                }
            } else if (propertyId != null) {
                throw new IllegalArgumentException("Platform Billing reports cannot carry property scope.");
            }
            provider = normalizeCode(provider, 40);
            method = normalizeCode(method, 40);
            transactionType = normalizeCode(transactionType, 40);
            roomType = normalizeCode(roomType, 80);
            planCode = normalizeCode(planCode, 80);
        }
    }

    public record ReportTotals(
            BigDecimal grossRevenue,
            BigDecimal refunds,
            BigDecimal credits,
            BigDecimal netRevenue,
            BigDecimal cashCollected,
            BigDecimal invoicedRevenue,
            BigDecimal unpaidBalance,
            BigDecimal heldDeposits,
            long successfulTransactionCount,
            long failedTransactionCount,
            long unreconciledTransactionCount) {

        public ReportTotals {
            grossRevenue = nonNegativeVnd(grossRevenue, "grossRevenue");
            refunds = nonNegativeVnd(refunds, "refunds");
            credits = nonNegativeVnd(credits, "credits");
            netRevenue = signedVnd(netRevenue, "netRevenue");
            cashCollected = nonNegativeVnd(cashCollected, "cashCollected");
            invoicedRevenue = nonNegativeVnd(invoicedRevenue, "invoicedRevenue");
            unpaidBalance = nonNegativeVnd(unpaidBalance, "unpaidBalance");
            heldDeposits = nonNegativeVnd(heldDeposits, "heldDeposits");
            requireNonNegative(successfulTransactionCount, "successfulTransactionCount");
            requireNonNegative(failedTransactionCount, "failedTransactionCount");
            requireNonNegative(unreconciledTransactionCount, "unreconciledTransactionCount");
            BigDecimal expectedNet = grossRevenue.subtract(refunds).subtract(credits);
            if (expectedNet.compareTo(netRevenue) != 0) {
                throw new IllegalArgumentException("netRevenue must equal grossRevenue minus refunds and credits.");
            }
        }
    }

    public record RevenueBreakdown(
            BreakdownDimension dimension,
            String code,
            String label,
            long transactionCount,
            BigDecimal grossRevenue,
            BigDecimal refunds,
            BigDecimal credits,
            BigDecimal netRevenue,
            boolean recurringEligible) {

        public RevenueBreakdown {
            dimension = Objects.requireNonNull(dimension, "dimension must not be null");
            code = normalizeCode(requireText(code, "code", 100), 100);
            label = normalizeOptional(label, 200);
            requireNonNegative(transactionCount, "transactionCount");
            grossRevenue = nonNegativeVnd(grossRevenue, "grossRevenue");
            refunds = nonNegativeVnd(refunds, "refunds");
            credits = nonNegativeVnd(credits, "credits");
            netRevenue = signedVnd(netRevenue, "netRevenue");
            BigDecimal expectedNet = grossRevenue.subtract(refunds).subtract(credits);
            if (expectedNet.compareTo(netRevenue) != 0) {
                throw new IllegalArgumentException("Breakdown netRevenue must reconcile to gross minus refunds and credits.");
            }
        }
    }

    public record RevenueTransactionRow(
            FinancialContext context,
            String publicId,
            Instant occurredAt,
            String transactionType,
            String sourceType,
            String sourceId,
            Long propertyId,
            String method,
            String provider,
            BigDecimal grossAmount,
            BigDecimal refundAmount,
            BigDecimal creditAmount,
            BigDecimal netAmount,
            Map<String, String> dimensions,
            ReconciliationStatus reconciliationStatus) {

        public RevenueTransactionRow {
            context = Objects.requireNonNull(context, "context must not be null");
            publicId = requireText(publicId, "publicId", 64);
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            transactionType = normalizeCode(requireText(transactionType, "transactionType", 40), 40);
            sourceType = normalizeCode(requireText(sourceType, "sourceType", 40), 40);
            sourceId = requireText(sourceId, "sourceId", 100);
            if (context == FinancialContext.PROPERTY_COMMERCE) {
                if (propertyId == null || propertyId <= 0) {
                    throw new IllegalArgumentException("Property Commerce rows require a positive propertyId.");
                }
            } else if (propertyId != null) {
                throw new IllegalArgumentException("Platform Billing rows cannot carry property scope.");
            }
            method = normalizeCode(method, 40);
            provider = normalizeCode(provider, 40);
            grossAmount = nonNegativeVnd(grossAmount, "grossAmount");
            refundAmount = nonNegativeVnd(refundAmount, "refundAmount");
            creditAmount = nonNegativeVnd(creditAmount, "creditAmount");
            netAmount = signedVnd(netAmount, "netAmount");
            if (grossAmount.subtract(refundAmount).subtract(creditAmount).compareTo(netAmount) != 0) {
                throw new IllegalArgumentException("Row netAmount must reconcile to gross minus refund and credit amounts.");
            }
            dimensions = immutableDimensions(dimensions);
            reconciliationStatus = Objects.requireNonNull(
                    reconciliationStatus, "reconciliationStatus must not be null");
        }
    }

    public record ReconciliationIssue(
            String code,
            String sourceType,
            String sourceId,
            BigDecimal expectedAmount,
            BigDecimal actualAmount,
            BigDecimal deltaAmount,
            String message) {

        public ReconciliationIssue {
            code = normalizeCode(requireText(code, "code", 80), 80);
            sourceType = normalizeCode(requireText(sourceType, "sourceType", 40), 40);
            sourceId = requireText(sourceId, "sourceId", 100);
            expectedAmount = signedVnd(expectedAmount, "expectedAmount");
            actualAmount = signedVnd(actualAmount, "actualAmount");
            deltaAmount = signedVnd(deltaAmount, "deltaAmount");
            if (actualAmount.subtract(expectedAmount).compareTo(deltaAmount) != 0) {
                throw new IllegalArgumentException("deltaAmount must equal actualAmount minus expectedAmount.");
            }
            message = requireText(message, "message", 500);
        }
    }

    public record RevenueReportResult(
            NormalizedFilters filters,
            ReportTotals totals,
            List<RevenueBreakdown> breakdowns,
            List<RevenueTransactionRow> rows,
            List<ReconciliationIssue> reconciliationIssues,
            long totalRowCount,
            String sourceWatermark,
            String checksum,
            Instant generatedAt) {

        public RevenueReportResult {
            filters = Objects.requireNonNull(filters, "filters must not be null");
            totals = Objects.requireNonNull(totals, "totals must not be null");
            breakdowns = List.copyOf(breakdowns == null ? List.of() : breakdowns);
            rows = List.copyOf(rows == null ? List.of() : rows);
            reconciliationIssues = List.copyOf(
                    reconciliationIssues == null ? List.of() : reconciliationIssues);
            requireNonNegative(totalRowCount, "totalRowCount");
            if (totalRowCount < rows.size()) {
                throw new IllegalArgumentException("totalRowCount cannot be smaller than the returned row count.");
            }
            for (RevenueTransactionRow row : rows) {
                if (row.context() != filters.context()) {
                    throw new IllegalArgumentException("Report rows must match the report context.");
                }
                if (filters.context() == FinancialContext.PROPERTY_COMMERCE
                        && !Objects.equals(row.propertyId(), filters.propertyId())) {
                    throw new IllegalArgumentException("Property report rows must match the normalized property scope.");
                }
            }
            sourceWatermark = requireText(sourceWatermark, "sourceWatermark", 200);
            checksum = normalizeOptional(checksum, 128);
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        }

        public FinancialContext context() {
            return filters.context();
        }

        public RecognitionBasis basis() {
            return filters.basis();
        }
    }

    private static Map<String, String> immutableDimensions(Map<String, String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new TreeMap<>();
        dimensions.forEach((key, value) -> normalized.put(
                normalizeCode(requireText(key, "dimension key", 80), 80),
                requireText(value, "dimension value", 200)));
        return Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
    }

    private static BigDecimal nonNegativeVnd(BigDecimal value, String field) {
        BigDecimal normalized = signedVnd(value, field);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative.");
        }
        return normalized;
    }

    private static BigDecimal signedVnd(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        return value.setScale(0, RoundingMode.UNNECESSARY);
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative.");
        }
    }

    private static String normalizeCode(String value, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("value is too long.");
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

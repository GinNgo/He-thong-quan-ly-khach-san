package com.hotel.paymentprovider.reporting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hotel.paymentprovider.reporting.RevenueReportModels.BreakdownDimension.PAYMENT_METHOD;
import static com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext.PLATFORM_BILLING;
import static com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext.PROPERTY_COMMERCE;
import static com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis.CASH_COLLECTED;
import static com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationStatus.RECONCILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevenueReportModelsTest {

    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void normalizesPropertyFiltersAndPreservesExplicitTenantScope() {
        RevenueReportModels.NormalizedFilters filters = propertyFilters();

        assertEquals(PROPERTY_COMMERCE, filters.context());
        assertEquals(42L, filters.propertyId());
        assertEquals("MOMO", filters.provider());
        assertEquals("BANK_QR", filters.method());
        assertEquals("DELUXE", filters.roomType());
    }

    @Test
    void rejectsMixedContextScopeAndInvalidDateRanges() {
        assertThrows(IllegalArgumentException.class, () -> new RevenueReportModels.NormalizedFilters(
                PLATFORM_BILLING, CASH_COLLECTED, FROM, TO, "UTC", 42L,
                null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new RevenueReportModels.NormalizedFilters(
                PROPERTY_COMMERCE, CASH_COLLECTED, FROM, TO, "UTC", null,
                null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new RevenueReportModels.NormalizedFilters(
                PLATFORM_BILLING, CASH_COLLECTED, TO, FROM, "UTC", null,
                null, null, null, null, null));
    }

    @Test
    void enforcesExactVndAndGrossRefundCreditNetEquation() {
        RevenueReportModels.ReportTotals totals = totals();

        assertEquals(new BigDecimal("850000"), totals.netRevenue());
        assertThrows(ArithmeticException.class, () -> new RevenueReportModels.ReportTotals(
                new BigDecimal("100.5"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.5"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RevenueReportModels.ReportTotals(
                new BigDecimal("1000"), new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("950"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0));
    }

    @Test
    void resultKeepsRowsAndDimensionsImmutableAndRejectsCrossContextRows() {
        Map<String, String> mutableDimensions = new LinkedHashMap<>();
        mutableDimensions.put("room_type", "Deluxe");
        RevenueReportModels.RevenueTransactionRow row = propertyRow(mutableDimensions);
        RevenueReportModels.RevenueReportResult result = new RevenueReportModels.RevenueReportResult(
                propertyFilters(),
                totals(),
                List.of(new RevenueReportModels.RevenueBreakdown(
                        PAYMENT_METHOD, "bank_qr", "Bank QR", 3,
                        new BigDecimal("1000000"), new BigDecimal("150000"), BigDecimal.ZERO,
                        new BigDecimal("850000"), false)),
                List.of(row),
                List.of(),
                1,
                "property-42:2026-08-01T00:00:00Z",
                null,
                Instant.parse("2026-08-01T00:05:00Z"));

        mutableDimensions.put("provider", "tampered");
        assertEquals(PROPERTY_COMMERCE, result.context());
        assertEquals(CASH_COLLECTED, result.basis());
        assertEquals(Map.of("ROOM_TYPE", "Deluxe"), result.rows().getFirst().dimensions());
        assertThrows(UnsupportedOperationException.class,
                () -> result.rows().getFirst().dimensions().put("PROVIDER", "MOMO"));

        RevenueReportModels.RevenueTransactionRow platformRow = new RevenueReportModels.RevenueTransactionRow(
                PLATFORM_BILLING, "platform-tx-1", FROM, "SUBSCRIPTION_PURCHASE", "ORDER", "order-1",
                null, "MOMO", "MOMO", new BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000000"), Map.of("plan", "PRO"), RECONCILED);
        assertThrows(IllegalArgumentException.class, () -> new RevenueReportModels.RevenueReportResult(
                propertyFilters(), totals(), List.of(), List.of(platformRow), List.of(), 1,
                "watermark", null, TO));
    }

    @Test
    void reconciliationIssueRequiresAnExactDelta() {
        RevenueReportModels.ReconciliationIssue issue = new RevenueReportModels.ReconciliationIssue(
                "ledger_mismatch", "transaction", "tx-1",
                new BigDecimal("100000"), new BigDecimal("90000"), new BigDecimal("-10000"),
                "Ledger and report totals differ.");

        assertTrue(issue.deltaAmount().signum() < 0);
        assertThrows(IllegalArgumentException.class, () -> new RevenueReportModels.ReconciliationIssue(
                "ledger_mismatch", "transaction", "tx-1",
                new BigDecimal("100000"), new BigDecimal("90000"), BigDecimal.ZERO,
                "Ledger and report totals differ."));
    }

    private RevenueReportModels.NormalizedFilters propertyFilters() {
        return new RevenueReportModels.NormalizedFilters(
                PROPERTY_COMMERCE,
                CASH_COLLECTED,
                FROM,
                TO,
                "Asia/Ho_Chi_Minh",
                42L,
                " momo ",
                " bank_qr ",
                " room_payment ",
                " deluxe ",
                null);
    }

    private RevenueReportModels.ReportTotals totals() {
        return new RevenueReportModels.ReportTotals(
                new BigDecimal("1000000"),
                new BigDecimal("150000"),
                BigDecimal.ZERO,
                new BigDecimal("850000"),
                new BigDecimal("900000"),
                new BigDecimal("1000000"),
                new BigDecimal("100000"),
                new BigDecimal("200000"),
                3,
                1,
                0);
    }

    private RevenueReportModels.RevenueTransactionRow propertyRow(Map<String, String> dimensions) {
        return new RevenueReportModels.RevenueTransactionRow(
                PROPERTY_COMMERCE,
                "property-tx-1",
                FROM,
                "room_payment",
                "reservation",
                "reservation-1",
                42L,
                "bank_qr",
                "momo",
                new BigDecimal("1000000"),
                new BigDecimal("150000"),
                BigDecimal.ZERO,
                new BigDecimal("850000"),
                dimensions,
                RECONCILED);
    }
}

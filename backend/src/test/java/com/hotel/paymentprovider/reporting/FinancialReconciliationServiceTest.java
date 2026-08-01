package com.hotel.paymentprovider.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationIssue;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationStatus;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReportTotals;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueTransactionRow;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.platformbilling.reporting.PlatformRevenueService;
import com.hotel.propertycommerce.reporting.PropertyRevenueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialReconciliationServiceTest {

    @Mock private PropertyRevenueService propertyRevenueService;
    @Mock private PlatformRevenueService platformRevenueService;

    @Test
    void reconcilesPlatformReportAndAllExportsFromOneResult() {
        RevenueReportResult report = report(List.of(), new BigDecimal("100000"));
        when(platformRevenueService.generate(report.filters())).thenReturn(report);
        FinancialReconciliationService service = new FinancialReconciliationService(
                propertyRevenueService, platformRevenueService, new RevenueExportService(),
                Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC));

        var run = service.reconcilePlatform(report.filters());

        assertTrue(run.reconciled());
        assertEquals(3, run.exports().size());
        assertEquals(0, run.mismatchQueue().size());
        assertTrue(run.exports().stream().allMatch(item ->
                item.checksumMatches() && item.rowCountMatches() && item.contentPresent()));
        verify(platformRevenueService).generate(report.filters());
        verify(propertyRevenueService, never()).generate(any());
    }

    @Test
    void queuesSourceReportAndExportMismatchesWithoutMutatingTheReport() {
        ReconciliationIssue sourceIssue = new ReconciliationIssue(
                "PLATFORM_PAYMENT_LEDGER_MISSING", "PAYMENT_ATTEMPT", "attempt-1",
                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("-100000"),
                "Successful attempt has no ledger debit.");
        RevenueReportResult report = report(List.of(sourceIssue), new BigDecimal("200000"));
        RevenueExportService exports = org.mockito.Mockito.mock(RevenueExportService.class);
        when(platformRevenueService.generate(report.filters())).thenReturn(report);
        when(exports.checksum(report)).thenReturn("expected-checksum");
        when(exports.export(any(), any())).thenAnswer(invocation -> new RevenueExportService.ExportArtifact(
                invocation.getArgument(1), new byte[0], "test/type", "report.test",
                "wrong-checksum", 0));
        FinancialReconciliationService service = new FinancialReconciliationService(
                propertyRevenueService, platformRevenueService, exports,
                Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC));

        var run = service.reconcile(report.filters());

        assertFalse(run.reconciled());
        assertTrue(run.mismatchQueue().stream().anyMatch(item ->
                item.category() == FinancialReconciliationService.MismatchCategory.SOURCE_EVIDENCE));
        assertTrue(run.mismatchQueue().stream().anyMatch(item ->
                item.code().equals("REPORT_ROW_GROSS_MISMATCH")));
        assertEquals(3, run.mismatchQueue().stream()
                .filter(item -> item.code().equals("EXPORT_CHECKSUM_MISMATCH")).count());
        assertEquals(1, report.rows().size());
    }

    private RevenueReportResult report(List<ReconciliationIssue> issues, BigDecimal totalGross) {
        NormalizedFilters filters = new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING, RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", null, null, null, null, null, null);
        RevenueTransactionRow row = new RevenueTransactionRow(
                FinancialContext.PLATFORM_BILLING, "TX-1", Instant.parse("2026-07-10T00:00:00Z"),
                "SUBSCRIPTION_PURCHASE", "PLATFORM_TRANSACTION", "TX-1", null, "QR", "MOMO",
                new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"),
                Map.of(), ReconciliationStatus.RECONCILED);
        return new RevenueReportResult(filters, new ReportTotals(
                totalGross, BigDecimal.ZERO, BigDecimal.ZERO, totalGross,
                totalGross, totalGross, BigDecimal.ZERO, BigDecimal.ZERO,
                1, 0, issues.size()), List.of(), List.of(row), issues, 1,
                "PLATFORM:2026-07-10T00:00:00Z", null, Instant.parse("2026-08-02T00:00:00Z"));
    }
}

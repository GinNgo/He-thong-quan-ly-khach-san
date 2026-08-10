package com.hotel.paymentprovider.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationStatus;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReportTotals;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueTransactionRow;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevenueExportServiceTest {

    private final RevenueExportService service = new RevenueExportService();

    @Test
    void allFormatsUseTheSameRowsTotalsAndChecksum() {
        RevenueReportResult report = report();
        String checksum = service.checksum(report);

        var csv = service.export(report, RevenueExportService.Format.CSV);
        var excel = service.export(report, RevenueExportService.Format.EXCEL);
        var pdf = service.export(report, RevenueExportService.Format.PDF);

        assertEquals(checksum, csv.checksum());
        assertEquals(checksum, excel.checksum());
        assertEquals(checksum, pdf.checksum());
        assertEquals(1, csv.rowCount());
        String csvText = new String(csv.content(), StandardCharsets.UTF_8);
        assertTrue(csvText.startsWith("\uFEFF"));
        assertTrue(csvText.contains("Mã giao dịch"));
        assertTrue(csvText.contains("TX-1"));
        assertTrue(new String(pdf.content(), StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"));
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(excel.content()))) {
            assertEquals("[Content_Types].xml", zip.getNextEntry().getName());
        } catch (Exception exception) {
            throw new AssertionError("Excel artifact is not a valid OOXML zip package", exception);
        }
    }

    @Test
    void checksumChangesWhenAReportRowChangesAndArtifactsDefensivelyCopyBytes() {
        RevenueReportResult first = report();
        RevenueReportResult second = new RevenueReportResult(
                first.filters(), first.totals(), first.breakdowns(), List.of(new RevenueTransactionRow(
                FinancialContext.PLATFORM_BILLING, "TX-2", Instant.parse("2026-07-10T00:00:00Z"),
                "SUBSCRIPTION_PURCHASE", "PLATFORM_TRANSACTION", "TX-2", null, "QR", "MOMO",
                new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"),
                Map.of(), ReconciliationStatus.RECONCILED)), first.reconciliationIssues(), 1,
                first.sourceWatermark(), null, first.generatedAt());

        assertTrue(!service.checksum(first).equals(service.checksum(second)));
        var artifact = service.export(first, RevenueExportService.Format.CSV);
        byte[] changed = artifact.content();
        changed[0] = 'X';
        assertTrue(artifact.content()[0] != 'X');
        assertArrayEquals(artifact.content(), artifact.content());
    }

    private RevenueReportResult report() {
        NormalizedFilters filters = new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING, RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", null, "MOMO", "QR", null, null, "PRO");
        RevenueTransactionRow row = new RevenueTransactionRow(
                FinancialContext.PLATFORM_BILLING, "TX-1", Instant.parse("2026-07-10T00:00:00Z"),
                "SUBSCRIPTION_PURCHASE", "PLATFORM_TRANSACTION", "TX-1", null, "QR", "MOMO",
                new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"),
                Map.of("PLAN_CODE", "PRO"), ReconciliationStatus.RECONCILED);
        return new RevenueReportResult(filters, new ReportTotals(
                new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"),
                new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO,
                1, 0, 0), List.of(), List.of(row), List.of(), 1,
                "PLATFORM:2026-07-10T00:00:00Z", null, Instant.parse("2026-08-02T00:00:00Z"));
    }
}

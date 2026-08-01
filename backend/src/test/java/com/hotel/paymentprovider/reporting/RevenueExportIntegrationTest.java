package com.hotel.paymentprovider.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationStatus;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReportTotals;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueTransactionRow;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevenueExportIntegrationTest {

    private final RevenueExportService service = new RevenueExportService();

    @Test
    void csvExcelAndPdfPreserveFiltersRowsTotalsAndChecksum() throws Exception {
        RevenueReportResult report = report();
        String expectedChecksum = service.checksum(report);

        var csv = service.export(report, RevenueExportService.Format.CSV);
        var excel = service.export(report, RevenueExportService.Format.EXCEL);
        var pdf = service.export(report, RevenueExportService.Format.PDF);
        String csvText = new String(csv.content(), StandardCharsets.UTF_8);
        String sheetXml = unzip(excel.content()).get("xl/worksheets/sheet1.xml");
        String pdfText = new String(pdf.content(), StandardCharsets.ISO_8859_1);

        for (var artifact : List.of(csv, excel, pdf)) {
            assertEquals(expectedChecksum, artifact.checksum());
            assertEquals(report.totalRowCount(), artifact.rowCount());
            assertTrue(artifact.content().length > 0);
        }
        for (String expected : List.of(
                expectedChecksum, "2026-07-01T00:00:00Z", "2026-08-01T00:00:00Z",
                "MOMO", "QR", "PRO", "150000", "TX-PURCHASE", "TX-REFUND")) {
            assertTrue(csvText.contains(expected), "CSV missing " + expected);
            assertTrue(sheetXml.contains(expected), "Excel missing " + expected);
            assertTrue(pdfText.contains(expected), "PDF missing " + expected);
        }
        assertArrayEquals(csv.content(), service.export(report, RevenueExportService.Format.CSV).content());
        assertArrayEquals(excel.content(), service.export(report, RevenueExportService.Format.EXCEL).content());
        assertArrayEquals(pdf.content(), service.export(report, RevenueExportService.Format.PDF).content());
    }

    private Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                entries.put(entry.getName(), output.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private RevenueReportResult report() {
        NormalizedFilters filters = new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING, RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", null, "MOMO", "QR", null, null, "PRO");
        RevenueTransactionRow purchase = new RevenueTransactionRow(
                FinancialContext.PLATFORM_BILLING, "TX-PURCHASE", Instant.parse("2026-07-10T00:00:00Z"),
                "SUBSCRIPTION_PURCHASE", "PLATFORM_TRANSACTION", "TX-PURCHASE", null, "QR", "MOMO",
                new BigDecimal("200000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200000"),
                Map.of("PLAN_CODE", "PRO"), ReconciliationStatus.RECONCILED);
        RevenueTransactionRow refund = new RevenueTransactionRow(
                FinancialContext.PLATFORM_BILLING, "TX-REFUND", Instant.parse("2026-07-20T00:00:00Z"),
                "SUBSCRIPTION_REFUND", "PLATFORM_TRANSACTION", "TX-REFUND", null, "QR", "MOMO",
                BigDecimal.ZERO, new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("-50000"),
                Map.of("PLAN_CODE", "PRO"), ReconciliationStatus.RECONCILED);
        return new RevenueReportResult(filters, new ReportTotals(
                new BigDecimal("200000"), new BigDecimal("50000"), BigDecimal.ZERO,
                new BigDecimal("150000"), new BigDecimal("200000"), new BigDecimal("150000"),
                BigDecimal.ZERO, BigDecimal.ZERO, 1, 0, 0),
                List.of(), List.of(purchase, refund), List.of(), 2,
                "PLATFORM:2026-07-20T00:00:00Z", null, Instant.parse("2026-08-02T00:00:00Z"));
    }
}

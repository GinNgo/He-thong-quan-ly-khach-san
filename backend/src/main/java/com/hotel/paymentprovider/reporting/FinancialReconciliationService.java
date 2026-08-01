package com.hotel.paymentprovider.reporting;

import com.hotel.paymentprovider.reporting.RevenueExportService.ExportArtifact;
import com.hotel.paymentprovider.reporting.RevenueExportService.Format;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationIssue;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.platformbilling.reporting.PlatformRevenueService;
import com.hotel.propertycommerce.reporting.PropertyRevenueService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runs read-only report and export reconciliation without mutating financial evidence. */
@Service
public class FinancialReconciliationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0);

    private final PropertyRevenueService propertyRevenueService;
    private final PlatformRevenueService platformRevenueService;
    private final RevenueExportService exportService;
    private final Clock clock;

    public FinancialReconciliationService(
            PropertyRevenueService propertyRevenueService,
            PlatformRevenueService platformRevenueService,
            RevenueExportService exportService) {
        this(propertyRevenueService, platformRevenueService, exportService, Clock.systemUTC());
    }

    FinancialReconciliationService(
            PropertyRevenueService propertyRevenueService,
            PlatformRevenueService platformRevenueService,
            RevenueExportService exportService,
            Clock clock) {
        this.propertyRevenueService = propertyRevenueService;
        this.platformRevenueService = platformRevenueService;
        this.exportService = exportService;
        this.clock = clock;
    }

    public ReconciliationRun reconcile(NormalizedFilters filters) {
        Objects.requireNonNull(filters, "filters must not be null");
        RevenueReportResult report = switch (filters.context()) {
            case PROPERTY_COMMERCE -> propertyRevenueService.generate(filters);
            case PLATFORM_BILLING -> platformRevenueService.generate(filters);
        };
        String checksum = exportService.checksum(report);
        List<MismatchQueueItem> mismatches = new ArrayList<>();
        report.reconciliationIssues().forEach(issue -> mismatches.add(sourceMismatch(issue)));
        reconcileReportRows(report, mismatches);

        List<ExportVerification> exports = new ArrayList<>();
        for (Format format : Format.values()) {
            ExportArtifact artifact = exportService.export(report, format);
            boolean checksumMatches = checksum.equals(artifact.checksum());
            boolean rowCountMatches = artifact.rowCount() == report.totalRowCount();
            boolean contentPresent = artifact.content().length > 0;
            if (!checksumMatches) {
                mismatches.add(mismatch("EXPORT_CHECKSUM_MISMATCH", "EXPORT", format.name(),
                        checksum, artifact.checksum(), "Export checksum differs from the report result checksum."));
            }
            if (!rowCountMatches) {
                mismatches.add(mismatch("EXPORT_ROW_COUNT_MISMATCH", "EXPORT", format.name(),
                        Long.toString(report.totalRowCount()), Long.toString(artifact.rowCount()),
                        "Export row count differs from the report result row count."));
            }
            if (!contentPresent) {
                mismatches.add(mismatch("EXPORT_CONTENT_MISSING", "EXPORT", format.name(),
                        "NON_EMPTY", "EMPTY", "Export renderer produced no content."));
            }
            exports.add(new ExportVerification(
                    format, artifact.contentType(), artifact.fileName(), artifact.checksum(),
                    artifact.rowCount(), artifact.content().length, checksumMatches, rowCountMatches, contentPresent));
        }
        return new ReconciliationRun(
                report.context(), report.filters(), report, checksum,
                List.copyOf(exports), List.copyOf(mismatches),
                mismatches.isEmpty(), clock.instant());
    }

    public ReconciliationRun reconcileProperty(NormalizedFilters filters) {
        requireContext(filters, FinancialContext.PROPERTY_COMMERCE);
        return reconcile(filters);
    }

    public ReconciliationRun reconcilePlatform(NormalizedFilters filters) {
        requireContext(filters, FinancialContext.PLATFORM_BILLING);
        return reconcile(filters);
    }

    private void reconcileReportRows(RevenueReportResult report, List<MismatchQueueItem> mismatches) {
        BigDecimal gross = report.rows().stream().map(RevenueReportModels.RevenueTransactionRow::grossAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal refunds = report.rows().stream().map(RevenueReportModels.RevenueTransactionRow::refundAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal credits = report.rows().stream().map(RevenueReportModels.RevenueTransactionRow::creditAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal net = report.rows().stream().map(RevenueReportModels.RevenueTransactionRow::netAmount)
                .reduce(ZERO, BigDecimal::add);
        compareAmount("REPORT_ROW_GROSS_MISMATCH", report.totals().grossRevenue(), gross, mismatches);
        compareAmount("REPORT_ROW_REFUND_MISMATCH", report.totals().refunds(), refunds, mismatches);
        compareAmount("REPORT_ROW_CREDIT_MISMATCH", report.totals().credits(), credits, mismatches);
        compareAmount("REPORT_ROW_NET_MISMATCH", report.totals().netRevenue(), net, mismatches);
        if (report.totalRowCount() != report.rows().size()) {
            mismatches.add(mismatch("REPORT_ROW_COUNT_MISMATCH", "REPORT", report.context().name(),
                    Long.toString(report.totalRowCount()), Integer.toString(report.rows().size()),
                    "Report total row count differs from the complete detail row set."));
        }
    }

    private void compareAmount(
            String code, BigDecimal expected, BigDecimal actual, List<MismatchQueueItem> mismatches) {
        if (expected.compareTo(actual) != 0) {
            mismatches.add(mismatch(code, "REPORT", code,
                    expected.toPlainString(), actual.toPlainString(),
                    "Report total does not reconcile with exported detail rows to one VND."));
        }
    }

    private MismatchQueueItem sourceMismatch(ReconciliationIssue issue) {
        return new MismatchQueueItem(
                issue.code(), MismatchCategory.SOURCE_EVIDENCE, issue.sourceType(), issue.sourceId(),
                issue.expectedAmount().toPlainString(), issue.actualAmount().toPlainString(), issue.message());
    }

    private MismatchQueueItem mismatch(
            String code, String sourceType, String sourceId,
            String expected, String actual, String message) {
        return new MismatchQueueItem(
                code,
                "EXPORT".equals(sourceType) ? MismatchCategory.EXPORT : MismatchCategory.REPORT,
                sourceType, sourceId, expected, actual, message);
    }

    private void requireContext(NormalizedFilters filters, FinancialContext expected) {
        Objects.requireNonNull(filters, "filters must not be null");
        if (filters.context() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " reconciliation filters.");
        }
    }

    public enum MismatchCategory {
        SOURCE_EVIDENCE,
        REPORT,
        EXPORT
    }

    public record MismatchQueueItem(
            String code,
            MismatchCategory category,
            String sourceType,
            String sourceId,
            String expectedValue,
            String actualValue,
            String message) {
    }

    public record ExportVerification(
            Format format,
            String contentType,
            String fileName,
            String checksum,
            long rowCount,
            long byteCount,
            boolean checksumMatches,
            boolean rowCountMatches,
            boolean contentPresent) {
    }

    public record ReconciliationRun(
            FinancialContext context,
            NormalizedFilters filters,
            RevenueReportResult report,
            String reportChecksum,
            List<ExportVerification> exports,
            List<MismatchQueueItem> mismatchQueue,
            boolean reconciled,
            Instant completedAt) {

        public ReconciliationRun {
            exports = List.copyOf(exports);
            mismatchQueue = List.copyOf(mismatchQueue);
        }
    }
}

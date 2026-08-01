package com.hotel.platformbilling.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.paymentprovider.reporting.RevenueExportService;
import com.hotel.paymentprovider.reporting.RevenueExportService.ExportArtifact;
import com.hotel.paymentprovider.reporting.RevenueExportService.Format;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

/** System-only Platform Billing revenue report endpoint. */
@RestController
public class PlatformRevenueController {

    private static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";

    private final PlatformRevenueService revenueService;
    private final RevenueExportService exportService;

    public PlatformRevenueController(PlatformRevenueService revenueService) {
        this(revenueService, new RevenueExportService());
    }

    @Autowired
    public PlatformRevenueController(
            PlatformRevenueService revenueService,
            RevenueExportService exportService) {
        this.revenueService = revenueService;
        this.exportService = exportService;
    }

    @GetMapping("/api/admin/reports/platform-revenue")
    @Permission(function = FunctionCode.PLATFORM_REVENUE, action = ActionCode.VIEW)
    public RevenueReportResult report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "NET") String basis,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String planCode,
            @RequestParam(defaultValue = DEFAULT_ZONE) String zoneId) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Report from date must be on or before the to date.");
        }
        ZoneId zone = parseZone(zoneId);
        return revenueService.generate(new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING,
                parseBasis(basis),
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant(),
                zone.getId(),
                null,
                provider,
                method,
                transactionType,
                null,
                planCode));
    }

    @GetMapping("/api/admin/reports/platform-revenue/export")
    @Permission(function = FunctionCode.PLATFORM_REVENUE, action = ActionCode.EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "NET") String basis,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String planCode,
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(defaultValue = DEFAULT_ZONE) String zoneId) {
        RevenueReportResult result = report(
                from, to, basis, provider, method, transactionType, planCode, zoneId);
        return exportResponse(exportService.export(result, parseFormat(format)));
    }

    private ResponseEntity<byte[]> exportResponse(ExportArtifact artifact) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + artifact.fileName() + "\"")
                .header("X-Report-Checksum", artifact.checksum())
                .header("X-Report-Row-Count", Long.toString(artifact.rowCount()))
                .body(artifact.content());
    }

    private Format parseFormat(String value) {
        try {
            return Format.valueOf(value == null ? "CSV" : value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported revenue export format.", exception);
        }
    }

    private ZoneId parseZone(String value) {
        if (value == null || value.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE);
        }
        try {
            return ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Unknown report time zone.", exception);
        }
    }

    private RecognitionBasis parseBasis(String value) {
        if (value == null || value.isBlank()) {
            return RecognitionBasis.NET;
        }
        try {
            return RecognitionBasis.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported revenue recognition basis.", exception);
        }
    }
}

package com.hotel.platformbilling.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import org.springframework.format.annotation.DateTimeFormat;
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

    public PlatformRevenueController(PlatformRevenueService revenueService) {
        this.revenueService = revenueService;
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

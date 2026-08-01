package com.hotel.propertycommerce.reporting;

import com.hotel.entities.User;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.PropertyAccessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

/** Property revenue API; property scope is resolved from authenticated access. */
@RestController
public class PropertyRevenueController {

    private static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";

    private final PropertyRevenueService revenueService;
    private final PropertyAccessService propertyAccessService;

    public PropertyRevenueController(
            PropertyRevenueService revenueService,
            PropertyAccessService propertyAccessService) {
        this.revenueService = revenueService;
        this.propertyAccessService = propertyAccessService;
    }

    @GetMapping("/api/management/reports/property-revenue")
    @Permission(function = FunctionCode.REPORT, action = ActionCode.VIEW)
    public RevenueReportResult report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "NET") String basis,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String roomType,
            @RequestParam(defaultValue = DEFAULT_ZONE) String zoneId) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Report from date must be on or before the to date.");
        }
        Long resolvedPropertyId = resolvePropertyId(propertyId);
        ZoneId zone = parseZone(zoneId);
        RecognitionBasis recognitionBasis = parseBasis(basis);
        return revenueService.generate(new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE,
                recognitionBasis,
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant(),
                zone.getId(),
                resolvedPropertyId,
                provider,
                method,
                transactionType,
                roomType,
                null));
    }

    private Long resolvePropertyId(Long requestedPropertyId) {
        User current = propertyAccessService.currentUser();
        Long currentHotelId = current.getHotel() == null ? null : current.getHotel().getId();
        Long resolved = requestedPropertyId == null ? currentHotelId : requestedPropertyId;
        if (resolved == null && propertyAccessService.accessibleHotelIds().size() == 1) {
            resolved = propertyAccessService.accessibleHotelIds().iterator().next();
        }
        propertyAccessService.requireAccessibleOrNotFound(resolved, "property");
        return resolved;
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

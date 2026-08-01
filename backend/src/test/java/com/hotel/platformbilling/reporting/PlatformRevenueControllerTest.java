package com.hotel.platformbilling.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReportTotals;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformRevenueControllerTest {

    @Mock
    private PlatformRevenueService revenueService;

    @Test
    void normalizesSystemScopeFiltersAndDateZone() {
        when(revenueService.generate(any(NormalizedFilters.class))).thenReturn(emptyReport());
        PlatformRevenueController controller = new PlatformRevenueController(revenueService);

        controller.report(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                "cash_collected", " momo ", " qr ", "subscription_purchase", " pro ",
                "Asia/Ho_Chi_Minh");

        ArgumentCaptor<NormalizedFilters> captor = ArgumentCaptor.forClass(NormalizedFilters.class);
        verify(revenueService).generate(captor.capture());
        NormalizedFilters filters = captor.getValue();
        assertEquals(FinancialContext.PLATFORM_BILLING, filters.context());
        assertEquals(RecognitionBasis.CASH_COLLECTED, filters.basis());
        assertEquals("MOMO", filters.provider());
        assertEquals("QR", filters.method());
        assertEquals("SUBSCRIPTION_PURCHASE", filters.transactionType());
        assertEquals("PRO", filters.planCode());
        assertEquals(Instant.parse("2026-06-30T17:00:00Z"), filters.fromInclusive());
        assertEquals(Instant.parse("2026-07-31T17:00:00Z"), filters.toExclusive());
    }

    @Test
    void rejectsInvalidDateBasisAndZone() {
        PlatformRevenueController controller = new PlatformRevenueController(revenueService);
        assertThrows(IllegalArgumentException.class, () -> controller.report(
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1),
                "NET", null, null, null, null, "UTC"));
        assertThrows(IllegalArgumentException.class, () -> controller.report(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1),
                "UNKNOWN", null, null, null, null, "UTC"));
        assertThrows(IllegalArgumentException.class, () -> controller.report(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1),
                "NET", null, null, null, null, "Mars/Phobos"));
    }

    private RevenueReportResult emptyReport() {
        NormalizedFilters filters = new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING, RecognitionBasis.NET,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
                "UTC", null, null, null, null, null, null);
        return new RevenueReportResult(filters, new ReportTotals(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, 0), List.of(), List.of(), List.of(), 0,
                "PLATFORM:EMPTY", null, Instant.parse("2026-08-02T00:00:00Z"));
    }
}

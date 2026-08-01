package com.hotel.propertycommerce.reporting;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReportTotals;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.services.PropertyAccessService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyRevenueControllerTest {

    @Mock
    private PropertyRevenueService revenueService;

    @Mock
    private PropertyAccessService propertyAccessService;

    @Mock
    private User currentUser;

    @Mock
    private Hotel currentHotel;

    @Test
    void resolvesCurrentHotelAndNormalizesDateBasisAndZone() {
        when(propertyAccessService.currentUser()).thenReturn(currentUser);
        when(currentUser.getHotel()).thenReturn(currentHotel);
        when(currentHotel.getId()).thenReturn(42L);
        when(revenueService.generate(org.mockito.ArgumentMatchers.any(NormalizedFilters.class)))
                .thenReturn(emptyReport());

        PropertyRevenueController controller = new PropertyRevenueController(
                revenueService, propertyAccessService);
        controller.report(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                "cash_collected",
                null,
                " momo ",
                " bank_qr ",
                " room_payment ",
                " deluxe ",
                "Asia/Ho_Chi_Minh");

        ArgumentCaptor<NormalizedFilters> captor = ArgumentCaptor.forClass(NormalizedFilters.class);
        verify(revenueService).generate(captor.capture());
        verify(propertyAccessService).requireAccessibleOrNotFound(42L, "property");
        NormalizedFilters filters = captor.getValue();
        assertEquals(FinancialContext.PROPERTY_COMMERCE, filters.context());
        assertEquals(RecognitionBasis.CASH_COLLECTED, filters.basis());
        assertEquals(42L, filters.propertyId());
        assertEquals(Instant.parse("2026-06-30T17:00:00Z"), filters.fromInclusive());
        assertEquals(Instant.parse("2026-07-31T17:00:00Z"), filters.toExclusive());
        assertEquals("MOMO", filters.provider());
        assertEquals("BANK_QR", filters.method());
        assertEquals("ROOM_PAYMENT", filters.transactionType());
        assertEquals("DELUXE", filters.roomType());
    }

    @Test
    void acceptsExplicitAccessiblePropertyForSystemUsers() {
        when(propertyAccessService.currentUser()).thenReturn(currentUser);
        when(revenueService.generate(org.mockito.ArgumentMatchers.any(NormalizedFilters.class)))
                .thenReturn(emptyReport());

        PropertyRevenueController controller = new PropertyRevenueController(
                revenueService, propertyAccessService);
        controller.report(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 1),
                "net",
                11L,
                null,
                null,
                null,
                null,
                "UTC");

        verify(propertyAccessService).requireAccessibleOrNotFound(11L, "property");
    }

    @Test
    void rejectsInvalidDateBasisAndZoneBeforeGeneratingAReport() {
        PropertyRevenueController controller = new PropertyRevenueController(
                revenueService, propertyAccessService);

        assertThrows(IllegalArgumentException.class, () -> controller.report(
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 1),
                "NET", 42L, null, null, null, null, "UTC"));

        when(propertyAccessService.currentUser()).thenReturn(currentUser);
        when(currentUser.getHotel()).thenReturn(currentHotel);
        when(currentHotel.getId()).thenReturn(42L);
        assertThrows(IllegalArgumentException.class, () -> controller.report(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 1),
                "UNKNOWN", 42L, null, null, null, null, "UTC"));
        assertThrows(IllegalArgumentException.class, () -> controller.report(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 1),
                "NET", 42L, null, null, null, null, "Mars/Phobos"));
    }

    private RevenueReportResult emptyReport() {
        NormalizedFilters filters = new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE,
                RecognitionBasis.NET,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                "UTC",
                42L,
                null,
                null,
                null,
                null,
                null);
        return new RevenueReportResult(
                filters,
                new ReportTotals(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0,
                        0,
                        0),
                List.of(),
                List.of(),
                List.of(),
                0,
                "PROPERTY:42:EMPTY",
                null,
                Instant.parse("2026-08-02T00:00:00Z"));
    }
}

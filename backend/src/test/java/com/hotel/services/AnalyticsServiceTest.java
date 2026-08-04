package com.hotel.services;

import com.hotel.dtos.AnalyticsDataDTO;
import com.hotel.paymentprovider.reporting.RevenueReportModels.BreakdownDimension;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReconciliationStatus;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueReportModels.ReportTotals;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueTransactionRow;
import com.hotel.platformbilling.reporting.PlatformRevenueService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    @Mock private PlatformRevenueService platformRevenueService;
    @Mock private HotelRepository hotelRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationRoomRepository reservationRoomRepository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(
                platformRevenueService,
                hotelRepository,
                roomRepository,
                reservationRepository,
                reservationRoomRepository,
                Clock.fixed(Instant.parse("2026-08-04T06:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void reconcilesSevenDayPlatformRevenueAndOperationalOccupancy() {
        when(platformRevenueService.generate(any())).thenReturn(report());
        when(roomRepository.countSystemOperationalRooms()).thenReturn(10L);
        when(reservationRoomRepository.countSystemAssignedOccupiedRoomsOn(any(LocalDate.class)))
                .thenAnswer(invocation -> invocation.<LocalDate>getArgument(0).equals(LocalDate.of(2026, 8, 4))
                        ? 4L : 2L);
        when(reservationRepository.countSystemLegacyOccupiedRoomsOn(any(LocalDate.class)))
                .thenAnswer(invocation -> invocation.<LocalDate>getArgument(0).equals(LocalDate.of(2026, 8, 4))
                        ? 1L : 0L);
        when(reservationRepository.countSystemBookingsCreatedBetween(any(), any())).thenReturn(6L);
        when(hotelRepository.countByApprovalStatusAndOperationStatusAndIsDemoFalse(
                "APPROVED", "ACTIVE")).thenReturn(3L);

        AnalyticsDataDTO result = service.getAnalyticsData(systemAdmin());

        assertEquals(new BigDecimal("120"), result.getTotalRevenue());
        assertEquals(6L, result.getTotalBookings());
        assertEquals(10L, result.getTotalRooms());
        assertEquals(5L, result.getOccupiedRooms());
        assertEquals(50.0, result.getOccupancyRate());
        assertEquals(List.of("29/07", "30/07", "31/07", "01/08", "02/08", "03/08", "04/08"),
                result.getLabels());
        assertEquals(new BigDecimal("50"), result.getRevenueData().getFirst());
        assertEquals(new BigDecimal("70"), result.getRevenueData().getLast());
        assertEquals(50, result.getOccupancyData().getLast());
        assertEquals("PLATFORM_BILLING_NET", result.getRevenueBasis());
        assertEquals("RECONCILED", result.getReconciliationStatus());
        assertEquals(LocalDate.of(2026, 7, 29), result.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 8, 4), result.getPeriodTo());
    }

    @Test
    void rejectsNonSystemAdminBeforeLoadingCrossPropertyData() {
        CustomUserDetails propertyUser = new CustomUserDetails(
                "owner", "hash", Set.of(new SimpleGrantedAuthority("PROPERTY_OWNER")),
                Map.of(), 9L, null, Map.of());

        assertThrows(AccessDeniedException.class, () -> service.getAnalyticsData(propertyUser));

        verify(platformRevenueService, never()).generate(any());
        verify(roomRepository, never()).countSystemOperationalRooms();
    }

    private RevenueReportResult report() {
        NormalizedFilters filters = new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING,
                RecognitionBasis.NET,
                Instant.parse("2026-07-28T17:00:00Z"),
                Instant.parse("2026-08-04T17:00:00Z"),
                "Asia/Ho_Chi_Minh", null, null, null, null, null, null);
        List<RevenueTransactionRow> rows = List.of(
                row("platform-1", Instant.parse("2026-07-29T02:00:00Z"), "50"),
                row("platform-2", Instant.parse("2026-08-04T02:00:00Z"), "70"));
        return new RevenueReportResult(
                filters,
                new ReportTotals(
                        amount("120"), amount("0"), amount("0"), amount("120"),
                        amount("120"), amount("120"), amount("0"), amount("0"),
                        2, 0, 0),
                List.of(new com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueBreakdown(
                        BreakdownDimension.TRANSACTION_TYPE, "PURCHASE", "Purchase", 2,
                        amount("120"), amount("0"), amount("0"), amount("120"), true)),
                rows,
                List.of(),
                rows.size(),
                "platform-watermark-2",
                "checksum",
                Instant.parse("2026-08-04T06:00:00Z"));
    }

    private RevenueTransactionRow row(String id, Instant occurredAt, String amount) {
        return new RevenueTransactionRow(
                FinancialContext.PLATFORM_BILLING,
                id,
                occurredAt,
                "SUBSCRIPTION_PURCHASE",
                "PLATFORM_LEDGER",
                id,
                null,
                "BANK_TRANSFER",
                "SANDBOX",
                amount(amount),
                amount("0"),
                amount("0"),
                amount(amount),
                Map.of("PLAN", "PRO"),
                ReconciliationStatus.RECONCILED);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(0);
    }

    private CustomUserDetails systemAdmin() {
        return new CustomUserDetails(
                "admin", "hash", Set.of(new SimpleGrantedAuthority("SUPER_ADMIN")),
                Map.of(), 1L, null, Map.of());
    }
}

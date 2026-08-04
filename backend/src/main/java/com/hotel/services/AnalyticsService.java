package com.hotel.services;

import com.hotel.dtos.AnalyticsDataDTO;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.platformbilling.reporting.PlatformRevenueService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int PERIOD_DAYS = 7;

    private final PlatformRevenueService platformRevenueService;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationRoomRepository reservationRoomRepository;
    private final Clock clock;

    @Autowired
    public AnalyticsService(
            PlatformRevenueService platformRevenueService,
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            ReservationRepository reservationRepository,
            ReservationRoomRepository reservationRoomRepository) {
        this(platformRevenueService, hotelRepository, roomRepository,
                reservationRepository, reservationRoomRepository, Clock.systemUTC());
    }

    AnalyticsService(
            PlatformRevenueService platformRevenueService,
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            ReservationRepository reservationRepository,
            ReservationRoomRepository reservationRoomRepository,
            Clock clock) {
        this.platformRevenueService = platformRevenueService;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.reservationRoomRepository = reservationRoomRepository;
        this.clock = clock;
    }

    public AnalyticsDataDTO getAnalyticsData(CustomUserDetails actor) {
        requireSystemAdministrator(actor);
        Instant generatedAt = clock.instant();
        LocalDate periodTo = generatedAt.atZone(DASHBOARD_ZONE).toLocalDate();
        LocalDate periodFrom = periodTo.minusDays(PERIOD_DAYS - 1L);
        Instant fromInclusive = periodFrom.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant toExclusive = periodTo.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();

        RevenueReportResult revenue = platformRevenueService.generate(new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING,
                RecognitionBasis.NET,
                fromInclusive,
                toExclusive,
                DASHBOARD_ZONE.getId(),
                null,
                null,
                null,
                null,
                null,
                null));

        Map<LocalDate, BigDecimal> revenueByDay = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>(PERIOD_DAYS);
        List<BigDecimal> revenueData = new ArrayList<>(PERIOD_DAYS);
        List<Integer> occupancyData = new ArrayList<>(PERIOD_DAYS);
        long totalRooms = roomRepository.countSystemOperationalRooms();
        long occupiedRoomsToday = 0;
        boolean operationsReconciled = true;

        revenue.rows().forEach(row -> revenueByDay.merge(
                row.occurredAt().atZone(DASHBOARD_ZONE).toLocalDate(),
                row.netAmount(),
                BigDecimal::add));

        for (int offset = 0; offset < PERIOD_DAYS; offset++) {
            LocalDate date = periodFrom.plusDays(offset);
            labels.add(LABEL_FORMAT.format(date));
            revenueData.add(revenueByDay.getOrDefault(date, BigDecimal.ZERO).setScale(0));
            long occupied = reservationRoomRepository.countSystemAssignedOccupiedRoomsOn(date)
                    + reservationRepository.countSystemLegacyOccupiedRoomsOn(date);
            if (occupied > totalRooms) operationsReconciled = false;
            long boundedOccupied = Math.min(occupied, totalRooms);
            if (date.equals(periodTo)) occupiedRoomsToday = boundedOccupied;
            occupancyData.add(percentage(boundedOccupied, totalRooms));
        }

        long totalBookings = reservationRepository.countSystemBookingsCreatedBetween(
                periodFrom.atStartOfDay(), periodTo.plusDays(1).atStartOfDay());
        long operationalProperties = hotelRepository
                .countByApprovalStatusAndOperationStatusAndIsDemoFalse("APPROVED", "ACTIVE");
        boolean financialReconciled = revenue.reconciliationIssues().isEmpty()
                && revenue.totals().unreconciledTransactionCount() == 0;

        AnalyticsDataDTO dto = new AnalyticsDataDTO();
        dto.setTotalRevenue(revenue.totals().netRevenue());
        dto.setTotalBookings(totalBookings);
        dto.setTotalRooms(totalRooms);
        dto.setOccupiedRooms(occupiedRoomsToday);
        dto.setOperationalProperties(operationalProperties);
        dto.setOccupancyRate(percentageDecimal(occupiedRoomsToday, totalRooms));
        dto.setScope("SYSTEM_NON_DEMO");
        dto.setRevenueBasis("PLATFORM_BILLING_NET");
        dto.setOccupancyBasis("ASSIGNED_AND_LEGACY_STAYS_OVER_OPERATIONAL_ROOMS");
        dto.setReconciliationStatus(financialReconciled && operationsReconciled
                ? "RECONCILED" : "UNRECONCILED");
        dto.setSourceWatermark(revenue.sourceWatermark());
        dto.setGeneratedAt(generatedAt);
        dto.setPeriodFrom(periodFrom);
        dto.setPeriodTo(periodTo);
        dto.setLabels(labels);
        dto.setRevenueData(revenueData);
        dto.setOccupancyData(occupancyData);
        return dto;
    }

    private void requireSystemAdministrator(CustomUserDetails actor) {
        if (actor == null || actor.getAuthorities().stream().noneMatch(authority ->
                "SUPER_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("System administrator context is required for the admin dashboard");
        }
    }

    private int percentage(long occupied, long total) {
        return (int) Math.round(percentageDecimal(occupied, total));
    }

    private double percentageDecimal(long occupied, long total) {
        if (total <= 0) return 0D;
        return BigDecimal.valueOf(occupied)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}

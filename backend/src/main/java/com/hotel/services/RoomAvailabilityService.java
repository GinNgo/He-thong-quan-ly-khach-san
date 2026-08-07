package com.hotel.services;

import com.hotel.dtos.RoomTypeDTO;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.propertycommerce.booking.ReservationAmendment;
import com.hotel.propertycommerce.booking.ReservationAmendmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoomAvailabilityService {

    public static final List<String> RELEASED_RESERVATION_STATUSES = List.of(
            "CANCELLED", "REJECTED", "EXPIRED", "NO_SHOW", "CHECKED_OUT", "COMPLETED"
    );
    private static final BigDecimal TAX_MULTIPLIER = new BigDecimal("1.15");

    private final RoomRepository roomRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final RoomAvailabilityPolicy roomAvailabilityPolicy;
    private final ReservationAmendmentRepository reservationAmendmentRepository;
    private final Clock clock = Clock.systemUTC();

    public long countAvailableRooms(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        boolean datedStay = hasStayDates(checkIn, checkOut);
        if (datedStay) validateStayDates(checkIn, checkOut);
        long roomsInPool = roomRepository.countRoomsInAvailabilityPool(
                roomTypeId,
                roomAvailabilityPolicy.roomStatuses(datedStay),
                roomAvailabilityPolicy.housekeepingStatuses());
        if (!datedStay) return roomsInPool;

        long reservedRooms = reservationDetailRepository.sumReservedQuantity(
                roomTypeId, RELEASED_RESERVATION_STATUSES, checkIn, checkOut
        );
        long quotedRooms = reservationAmendmentRepository.sumActiveHoldQuantity(
                roomTypeId,
                activeAmendmentStatuses(),
                checkIn,
                checkOut,
                null,
                now());
        return Math.max(0, roomsInPool - reservedRooms - quotedRooms);
    }

    public Map<Long, Long> countAvailableRooms(Collection<Long> roomTypeIds,
                                               LocalDate checkIn, LocalDate checkOut) {
        if (roomTypeIds == null || roomTypeIds.isEmpty()) return Map.of();
        boolean datedStay = hasStayDates(checkIn, checkOut);
        if (datedStay) validateStayDates(checkIn, checkOut);
        Map<Long, Long> available = new HashMap<>();
        roomTypeIds.forEach(id -> available.put(id, 0L));
        roomRepository.countRoomsInAvailabilityPoolByRoomTypeIds(
                        roomTypeIds,
                        roomAvailabilityPolicy.roomStatuses(datedStay),
                        roomAvailabilityPolicy.housekeepingStatuses())
                .forEach(row -> available.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        if (!datedStay) return Map.copyOf(available);

        reservationDetailRepository.sumReservedQuantityByRoomTypeIds(
                        roomTypeIds, RELEASED_RESERVATION_STATUSES, checkIn, checkOut)
                .forEach(row -> {
                    Long roomTypeId = ((Number) row[0]).longValue();
                    long reserved = ((Number) row[1]).longValue();
                    available.computeIfPresent(roomTypeId, (id, physical) -> Math.max(0, physical - reserved));
                });
        reservationAmendmentRepository.sumActiveHoldQuantityByRoomTypeIds(
                        roomTypeIds, activeAmendmentStatuses(), checkIn, checkOut, now())
                .forEach(row -> {
                    Long roomTypeId = ((Number) row[0]).longValue();
                    long quoted = ((Number) row[1]).longValue();
                    available.computeIfPresent(roomTypeId, (id, physical) -> Math.max(0, physical - quoted));
                });
        return Map.copyOf(available);
    }

    public long countAvailableRoomsExcludingReservation(
            Long roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            Long reservationId,
            Long excludedQuoteId,
            LocalDateTime now) {
        validateStayDates(checkIn, checkOut);
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId is required.");
        }
        long roomsInPool = roomRepository.countRoomsInAvailabilityPool(
                roomTypeId,
                roomAvailabilityPolicy.roomStatuses(true),
                roomAvailabilityPolicy.housekeepingStatuses());
        long reservedRooms = reservationDetailRepository.sumReservedQuantityExcludingReservation(
                roomTypeId, reservationId, RELEASED_RESERVATION_STATUSES, checkIn, checkOut);
        long quotedRooms = reservationAmendmentRepository.sumActiveHoldQuantity(
                roomTypeId, activeAmendmentStatuses(), checkIn, checkOut, excludedQuoteId,
                now == null ? now() : now);
        return Math.max(0, roomsInPool - reservedRooms - quotedRooms);
    }

    public Room findFirstAvailableRoomForBooking(Long roomTypeId, LocalDate checkIn, LocalDate checkOut, Integer guests) {
        validateStayDates(checkIn, checkOut);
        return roomRepository.findRoomsInDatedAvailabilityPoolForUpdate(
                        roomTypeId,
                        roomAvailabilityPolicy.roomStatuses(true),
                        roomAvailabilityPolicy.housekeepingStatuses(),
                        RELEASED_RESERVATION_STATUSES,
                        checkIn,
                        checkOut
                ).stream()
                .filter(room -> canHost(room.getRoomType(), guests))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Rất tiếc, loại phòng này đã hết chỗ trong khoảng ngày bạn chọn."
                ));
    }

    public void enrich(RoomTypeDTO dto, RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        dto.setAvailableRooms(countAvailableRooms(roomType.getId(), checkIn, checkOut));
        if (hasStayDates(checkIn, checkOut)) {
            long nights = getNights(checkIn, checkOut);
            dto.setNights(nights);
            dto.setTotalPrice(calculateTotal(roomType.getBasePrice(), nights));
        }
    }

    public long getNights(LocalDate checkIn, LocalDate checkOut) {
        validateStayDates(checkIn, checkOut);
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public BigDecimal calculateTotal(BigDecimal nightlyPrice, long nights) {
        return nightlyPrice.multiply(BigDecimal.valueOf(nights)).multiply(TAX_MULTIPLIER);
    }

    public BigDecimal calculateTotal(BigDecimal nightlyPrice, long nights, int quantity) {
        return calculateTotal(nightlyPrice, nights).multiply(BigDecimal.valueOf(quantity));
    }

    public boolean canHost(RoomType roomType, Integer guests) {
        Integer capacity = roomType.getMaxGuests() != null ? roomType.getMaxGuests() : roomType.getMaxGuest();
        return guests == null || guests <= 0 || capacity == null || capacity >= guests;
    }

    public boolean canHost(RoomType roomType, int quantity, int adults, int children) {
        int maxAdults = firstPositive(roomType.getMaxAdults(), roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxChildren = firstNonNegative(roomType.getMaxChildren(), roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxGuests = firstPositive(roomType.getMaxGuests(), roomType.getMaxGuest());
        return adults <= maxAdults * quantity
                && children <= maxChildren * quantity
                && adults + children <= maxGuests * quantity;
    }

    public void validateCapacity(RoomType roomType, int quantity, int adults, int children) {
        int maxAdults = firstPositive(roomType.getMaxAdults(), roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxChildren = firstNonNegative(roomType.getMaxChildren(), roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxGuests = firstPositive(roomType.getMaxGuests(), roomType.getMaxGuest());

        if (adults > maxAdults * quantity) {
            throw new IllegalArgumentException("Số người lớn vượt quá sức chứa của loại phòng.");
        }
        if (children > maxChildren * quantity) {
            throw new IllegalArgumentException("Số trẻ em vượt quá sức chứa của loại phòng.");
        }
        if (adults + children > maxGuests * quantity) {
            throw new IllegalArgumentException("Tổng số khách vượt quá sức chứa của loại phòng.");
        }
    }

    private boolean hasStayDates(LocalDate checkIn, LocalDate checkOut) {
        return checkIn != null && checkOut != null;
    }

    private void validateStayDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Vui lòng chọn ngày nhận phòng và ngày trả phòng.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Ngày trả phòng phải sau ngày nhận phòng.");
        }
    }

    private int firstPositive(Integer... values) {
        for (Integer value : values) {
            if (value != null && value > 0) return value;
        }
        return Integer.MAX_VALUE;
    }

    private int firstNonNegative(Integer... values) {
        for (Integer value : values) {
            if (value != null && value >= 0) return value;
        }
        return Integer.MAX_VALUE;
    }

    private List<ReservationAmendment.Status> activeAmendmentStatuses() {
        return List.of(
                ReservationAmendment.Status.QUOTED,
                ReservationAmendment.Status.AWAITING_PAYMENT,
                ReservationAmendment.Status.PAYMENT_PENDING);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

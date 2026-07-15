package com.hotel.services;

import com.hotel.dtos.RoomTypeDTO;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomAvailabilityService {

    private static final List<String> EXCLUDED_ROOM_STATUSES = List.of(
            "MAINTENANCE", "OUT_OF_SERVICE", "DIRTY", "CLEANING", "OCCUPIED"
    );
    public static final List<String> RELEASED_RESERVATION_STATUSES = List.of(
            "CANCELLED", "REJECTED", "EXPIRED", "NO_SHOW", "CHECKED_OUT", "COMPLETED"
    );
    private static final BigDecimal TAX_MULTIPLIER = new BigDecimal("1.15");

    private final RoomRepository roomRepository;
    private final ReservationDetailRepository reservationDetailRepository;

    public long countAvailableRooms(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        long totalActiveRooms = roomRepository.countBookableRoomsByRoomTypeId(roomTypeId, EXCLUDED_ROOM_STATUSES);
        if (!hasStayDates(checkIn, checkOut)) {
            return totalActiveRooms;
        }

        validateStayDates(checkIn, checkOut);
        long reservedRooms = reservationDetailRepository.sumReservedQuantity(
                roomTypeId, RELEASED_RESERVATION_STATUSES, checkIn, checkOut
        );
        return Math.max(0, totalActiveRooms - reservedRooms);
    }

    public Room findFirstAvailableRoomForBooking(Long roomTypeId, LocalDate checkIn, LocalDate checkOut, Integer guests) {
        validateStayDates(checkIn, checkOut);
        return roomRepository.findAvailableRoomsByRoomTypeAndDateForUpdate(
                        roomTypeId,
                        EXCLUDED_ROOM_STATUSES,
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
}

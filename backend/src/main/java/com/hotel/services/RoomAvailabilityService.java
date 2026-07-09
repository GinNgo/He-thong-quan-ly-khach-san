package com.hotel.services;

import com.hotel.dtos.RoomTypeDTO;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
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

    private static final List<String> EXCLUDED_ROOM_STATUSES = List.of("MAINTENANCE");
    private static final List<String> EXCLUDED_RESERVATION_STATUSES = List.of("CANCELLED", "CHECKED_OUT", "COMPLETED");
    private static final BigDecimal TAX_MULTIPLIER = new BigDecimal("1.15");

    private final RoomRepository roomRepository;

    public long countAvailableRooms(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        if (hasStayDates(checkIn, checkOut)) {
            validateStayDates(checkIn, checkOut);
            return roomRepository.findAvailableRoomsByRoomTypeAndDate(
                    roomTypeId,
                    EXCLUDED_ROOM_STATUSES,
                    EXCLUDED_RESERVATION_STATUSES,
                    checkIn,
                    checkOut
            ).size();
        }

        return roomRepository.countBookableRoomsByRoomTypeId(roomTypeId, EXCLUDED_ROOM_STATUSES);
    }

    public Room findFirstAvailableRoomForBooking(Long roomTypeId, LocalDate checkIn, LocalDate checkOut, Integer guests) {
        validateStayDates(checkIn, checkOut);

        return roomRepository.findAvailableRoomsByRoomTypeAndDateForUpdate(
                        roomTypeId,
                        EXCLUDED_ROOM_STATUSES,
                        EXCLUDED_RESERVATION_STATUSES,
                        checkIn,
                        checkOut
                ).stream()
                .filter(room -> canHost(room.getRoomType(), guests))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Rất tiếc, loại phòng này đã hết chỗ trong khoảng ngày bạn chọn. Vui lòng chọn ngày hoặc loại phòng khác."));
    }

    public void enrich(RoomTypeDTO dto, RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        long availableRooms = countAvailableRooms(roomType.getId(), checkIn, checkOut);
        dto.setAvailableRooms(availableRooms);

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
        return nightlyPrice
                .multiply(BigDecimal.valueOf(nights))
                .multiply(TAX_MULTIPLIER);
    }

    public boolean canHost(RoomType roomType, Integer guests) {
        return guests == null || guests <= 0 || roomType.getMaxGuest() == null || roomType.getMaxGuest() >= guests;
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
}

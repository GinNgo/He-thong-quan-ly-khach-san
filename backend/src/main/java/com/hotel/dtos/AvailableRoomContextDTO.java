package com.hotel.dtos;

import java.time.LocalDate;
import java.util.List;

public record AvailableRoomContextDTO(
        Long reservationId,
        Long hotelId,
        Long roomTypeId,
        String roomTypeName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int requiredQuantity,
        List<RoomDTO> assignedRooms,
        List<Long> assignedRoomIds,
        List<RoomDTO> candidates) {
}

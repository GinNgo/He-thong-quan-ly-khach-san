package com.hotel.dtos;

import java.time.LocalDateTime;

public record HousekeepingTaskDTO(
        Long id,
        Long hotelId,
        Long roomId,
        String roomNumber,
        Long reservationId,
        String status,
        Long assignedToUserId,
        String assignedToUsername,
        String assignedToName,
        LocalDateTime assignedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String note,
        Long version,
        boolean staleAssignment,
        String roomStatus,
        String roomHousekeepingStatus,
        String roomMaintenanceStatus,
        boolean roomReleased) {
}

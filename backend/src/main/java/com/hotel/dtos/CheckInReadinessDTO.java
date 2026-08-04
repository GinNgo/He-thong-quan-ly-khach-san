package com.hotel.dtos;

import java.time.OffsetDateTime;
import java.util.List;

public record CheckInReadinessDTO(
        Long reservationId,
        String reservationStatus,
        boolean ready,
        boolean alreadyCheckedIn,
        OffsetDateTime evaluatedAt,
        OffsetDateTime scheduledArrivalAt,
        OffsetDateTime earliestCheckInAt,
        OffsetDateTime latestCheckInAt,
        String zoneId,
        long earlyWindowMinutes,
        String policyVersion,
        int requiredRoomCount,
        List<RoomDTO> assignedRooms,
        List<CheckInReadinessIssueDTO> blockers) {
}

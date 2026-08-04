package com.hotel.dtos;

import java.time.LocalDateTime;

public record PropertyReviewHistoryItem(
        Long eventId,
        Long propertyId,
        String eventType,
        String actorKind,
        String note,
        StatusTriplet beforeState,
        StatusTriplet afterState,
        LocalDateTime occurredAt) {

    public record StatusTriplet(
            String status,
            String approvalStatus,
            String operationStatus,
            String ownershipStatus) {
    }
}

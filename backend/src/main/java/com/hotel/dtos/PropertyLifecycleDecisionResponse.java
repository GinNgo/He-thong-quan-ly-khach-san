package com.hotel.dtos;

import java.time.LocalDateTime;

public record PropertyLifecycleDecisionResponse(
        Long propertyId,
        String status,
        String approvalStatus,
        String operationStatus,
        String action,
        boolean changed,
        Long actorUserId,
        LocalDateTime changedAt,
        String reason) {
}

package com.hotel.dtos;

import java.time.LocalDateTime;

public record PropertyApprovalDecisionResponse(
        Long propertyId,
        String status,
        String approvalStatus,
        String operationStatus,
        String ownershipStatus,
        Long reviewedByUserId,
        LocalDateTime reviewedAt,
        String reason) {
}

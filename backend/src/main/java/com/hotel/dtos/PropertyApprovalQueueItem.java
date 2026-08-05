package com.hotel.dtos;

import java.time.LocalDateTime;

public record PropertyApprovalQueueItem(
        Long propertyId,
        String code,
        String name,
        String address,
        String propertyType,
        String status,
        String approvalStatus,
        String operationStatus,
        String ownershipStatus,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        Long submittedByUserId,
        LocalDateTime submittedAt,
        Long reviewedByUserId,
        LocalDateTime reviewedAt,
        String reason) {
}

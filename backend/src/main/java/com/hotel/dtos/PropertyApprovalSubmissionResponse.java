package com.hotel.dtos;

import java.time.LocalDateTime;

public record PropertyApprovalSubmissionResponse(
        Long propertyId,
        String status,
        String approvalStatus,
        String operationStatus,
        Long submittedByUserId,
        LocalDateTime submittedAt) {
}

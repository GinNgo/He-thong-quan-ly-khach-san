package com.hotel.dtos;

import java.time.LocalDateTime;

public record PropertyClaimResponseDTO(
        Long id,
        PropertySummary property,
        UserSummary requesterUser,
        String verificationMethod,
        String verificationData,
        String note,
        String status,
        UserSummary reviewedBy,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime createdAt) {

    public record PropertySummary(
            Long id,
            String code,
            String name,
            String status,
            String approvalStatus,
            String operationStatus) {
        public PropertySummary(
                Long id,
                String code,
                String name,
                String approvalStatus,
                String operationStatus) {
            this(id, code, name, null, approvalStatus, operationStatus);
        }
    }

    public record UserSummary(
            Long id,
            String username,
            String email,
            String fullName) {
    }
}

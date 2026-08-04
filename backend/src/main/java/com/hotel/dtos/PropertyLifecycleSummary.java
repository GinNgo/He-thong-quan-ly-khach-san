package com.hotel.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record PropertyLifecycleSummary(
        Long propertyId,
        String code,
        String name,
        String address,
        String propertyType,
        String status,
        String approvalStatus,
        String operationStatus,
        String lifecycleAction,
        String lifecycleReason,
        Long lifecycleChangedByUserId,
        LocalDateTime lifecycleChangedAt,
        List<String> allowedTransitions) {
}

package com.hotel.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SubscriptionUsageDTO(
        Long targetHotelId,
        String source,
        boolean platformAuthoritative,
        String planCode,
        String subscriptionStatus,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        boolean lifetime,
        Map<String, Integer> limits,
        Map<String, Long> usage,
        List<SubscriptionEntitlementDTO> features,
        String migrationBlocker) {
}

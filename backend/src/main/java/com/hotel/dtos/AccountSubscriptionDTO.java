package com.hotel.dtos;

import java.time.LocalDateTime;

public record AccountSubscriptionDTO(
        Long targetHotelId,
        String source,
        boolean platformAuthoritative,
        Long planId,
        String planCode,
        String planName,
        String status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        boolean lifetime,
        String sourceReference,
        String migrationBlocker) {
}

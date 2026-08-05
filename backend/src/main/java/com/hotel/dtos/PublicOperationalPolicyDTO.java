package com.hotel.dtos;

import java.time.LocalDateTime;

public record PublicOperationalPolicyDTO(
        Long version,
        LocalDateTime effectiveFrom,
        String locale,
        String checkIn,
        String checkOut,
        String cancellation,
        String childPolicy,
        String petPolicy,
        String smokingPolicy,
        String houseRules) {
}

package com.hotel.dtos.home;

import java.time.Instant;
import java.util.Map;

public record HomeSpotlightDTO(
        Long id,
        String kind,
        String title,
        String description,
        String imageUrl,
        String imageAlt,
        String disclosure,
        Target target,
        Instant startsAt,
        Instant endsAt) {

    public record Target(
            String type,
            Long propertyId,
            String route,
            Map<String, String> query) {
    }
}


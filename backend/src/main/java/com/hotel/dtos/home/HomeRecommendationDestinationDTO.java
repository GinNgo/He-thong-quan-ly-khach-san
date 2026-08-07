package com.hotel.dtos.home;

public record HomeRecommendationDestinationDTO(
        Long id,
        String name,
        String displayName,
        long propertyCount,
        boolean selectedByDefault) {
}

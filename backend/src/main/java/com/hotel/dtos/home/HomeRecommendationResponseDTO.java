package com.hotel.dtos.home;

import java.util.List;

public record HomeRecommendationResponseDTO(
        HomeRecommendationDestinationDTO destination,
        List<HomeRecommendationItemDTO> items,
        long totalAvailable) {

    public HomeRecommendationResponseDTO {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

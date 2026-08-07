package com.hotel.dtos.home;

import com.hotel.dtos.PromotionQuoteDTO;

import java.math.BigDecimal;

public record HomeRecommendationItemDTO(
        Long propertyId,
        String name,
        String propertyType,
        Long provinceId,
        String provinceName,
        String wardName,
        String imageUrl,
        String imageAlt,
        Integer starRating,
        Double reviewScore,
        Integer reviewCount,
        Integer availableRoomCount,
        PricingSummary pricing,
        PromotionQuoteDTO quote,
        RecommendationReason recommendationReason,
        boolean sponsored) {

    public record PricingSummary(
            BigDecimal nightlyPrice,
            BigDecimal finalNightlyPrice,
            BigDecimal totalDiscount,
            String currency) {
    }

    public enum RecommendationReason {
        SEARCH_CONTEXT,
        POPULAR_DESTINATION,
        TOP_RATED
    }
}

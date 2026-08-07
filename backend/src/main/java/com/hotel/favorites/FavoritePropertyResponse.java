package com.hotel.favorites;

import java.time.LocalDateTime;

public record FavoritePropertyResponse(
        Long favoriteId,
        Long hotelId,
        String name,
        String slug,
        String addressLine,
        String city,
        String imageUrl,
        String propertyType,
        Double averageRating,
        Integer reviewCount,
        Double minPrice,
        LocalDateTime favoritedAt) {
}

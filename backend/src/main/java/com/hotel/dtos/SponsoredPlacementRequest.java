package com.hotel.dtos;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record SponsoredPlacementRequest(
        Long hotelId,
        @NotNull PlacementSurface placementSurface,
        @NotNull PlacementKind placementKind,
        @NotBlank @Size(max = 255) String titleVi,
        @NotBlank @Size(max = 255) String titleEn,
        @Size(max = 1000) String descriptionVi,
        @Size(max = 1000) String descriptionEn,
        @NotBlank @Size(max = 1000) String imageUrl,
        @NotBlank @Size(max = 500) String imageAltVi,
        @NotBlank @Size(max = 500) String imageAltEn,
        @NotNull TargetType targetType,
        Long targetHotelId,
        Map<String, String> targetQuery,
        Long targetProvinceId,
        Long targetLandmarkId,
        @NotNull Instant startsAt,
        @NotNull @Future Instant endsAt,
        Integer sortPriority,
        @PositiveOrZero BigDecimal budget,
        @PositiveOrZero Long impressionLimit,
        @PositiveOrZero Long clickLimit) {

    public enum PlacementSurface { HOME_PARTNER_SPOTLIGHT, SEARCH_RESULTS }

    public enum PlacementKind { EDITORIAL, SPONSORED }

    public enum TargetType { PROPERTY, SEARCH_COLLECTION }
}


package com.hotel.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

public record SponsoredPlacementDTO(
        Long id,
        Long hotelId,
        SponsoredPlacementRequest.PlacementSurface placementSurface,
        SponsoredPlacementRequest.PlacementKind placementKind,
        PlacementStatus status,
        String titleVi,
        String titleEn,
        String descriptionVi,
        String descriptionEn,
        String imageUrl,
        String imageAltVi,
        String imageAltEn,
        SponsoredPlacementRequest.TargetType targetType,
        Long targetHotelId,
        Map<String, String> targetQuery,
        Long targetProvinceId,
        Long targetLandmarkId,
        Instant startsAt,
        Instant endsAt,
        Integer sortPriority,
        BigDecimal budget,
        BigDecimal spentAmount,
        Long impressionLimit,
        Long impressionCount,
        Long clickLimit,
        Long clickCount,
        Long approvedByUserId,
        Instant approvedAt,
        String rejectedReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public enum PlacementStatus { DRAFT, SCHEDULED, ACTIVE, PAUSED, EXPIRED, REJECTED }
}


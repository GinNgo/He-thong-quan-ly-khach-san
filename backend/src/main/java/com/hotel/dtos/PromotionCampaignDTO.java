package com.hotel.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

public record PromotionCampaignDTO(
        Long id,
        String code,
        PromotionCampaignRequest.OwnerType ownerType,
        Long hotelId,
        PromotionCampaignRequest.ApplicationType applicationType,
        String nameVi,
        String nameEn,
        PromotionCampaignRequest.DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        Instant startsAt,
        Instant endsAt,
        String timezone,
        Map<String, Object> eligibility,
        BigDecimal budget,
        Long redemptionLimit,
        Long perCustomerLimit,
        PromotionCampaignRequest.StackingPolicy stackingPolicy,
        Integer priority,
        CampaignStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public enum CampaignStatus { DRAFT, SCHEDULED, ACTIVE, PAUSED, EXPIRED, REJECTED }
}


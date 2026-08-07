package com.hotel.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record PromotionCampaignRequest(
        @NotBlank @Size(max = 80) String code,
        @NotNull OwnerType ownerType,
        Long hotelId,
        @NotNull ApplicationType applicationType,
        @NotBlank @Size(max = 255) String nameVi,
        @Size(max = 255) String nameEn,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal discountValue,
        @PositiveOrZero BigDecimal maxDiscount,
        @NotNull Instant startsAt,
        @NotNull @Future Instant endsAt,
        @NotBlank @Size(max = 64) String timezone,
        Map<String, Object> eligibility,
        @PositiveOrZero BigDecimal budget,
        @PositiveOrZero Long redemptionLimit,
        @PositiveOrZero Long perCustomerLimit,
        @NotNull StackingPolicy stackingPolicy,
        Integer priority) {

    public enum OwnerType { SYSTEM, TENANT }

    public enum ApplicationType { AUTOMATIC, COUPON }

    public enum DiscountType { PERCENT, FIXED }

    public enum StackingPolicy { NO_COUPON, ALLOW_ONE_COUPON }
}


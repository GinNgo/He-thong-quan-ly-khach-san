package com.hotel.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PublicPromotionDTO(
        Long id,
        String code,
        Long propertyId,
        String nameVi,
        String nameEn,
        String applicationType,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscount,
        Instant endsAt,
        boolean memberOnly,
        List<String> requiredTierCodes) {
}

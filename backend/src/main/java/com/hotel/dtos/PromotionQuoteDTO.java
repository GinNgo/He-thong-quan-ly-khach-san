package com.hotel.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PromotionQuoteDTO(
        String quoteId,
        Instant expiresAt,
        Long propertyId,
        Long roomTypeId,
        BigDecimal nightlyPrice,
        int numberOfNights,
        int roomQuantity,
        BigDecimal baseSubtotal,
        BigDecimal taxAmount,
        BigDecimal feeAmount,
        BigDecimal taxesAndFees,
        List<AppliedPromotion> appliedPromotions,
        MemberBenefit memberBenefit,
        BigDecimal totalDiscount,
        BigDecimal finalTotal,
        String currency) {

    public record AppliedPromotion(
            Long campaignId,
            String code,
            String applicationType,
            String nameVi,
            String nameEn,
            BigDecimal discountAmount) {
    }

    public record MemberBenefit(
            boolean eligible,
            String tierCode,
            String tierNameVi,
            String tierNameEn,
            String explanation) {
    }
}

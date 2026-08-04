package com.hotel.dtos;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionPlanDTO(
        Long id,
        String code,
        String nameVi,
        String nameEn,
        String billingType,
        BigDecimal price,
        String currency,
        boolean lifetime,
        String status,
        List<SubscriptionFeatureDTO> features) {
}

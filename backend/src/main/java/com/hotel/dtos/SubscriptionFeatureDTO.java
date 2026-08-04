package com.hotel.dtos;

public record SubscriptionFeatureDTO(
        String code,
        String nameVi,
        String nameEn,
        String valueType,
        Integer limit) {
}

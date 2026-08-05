package com.hotel.dtos;

public record SubscriptionEntitlementDTO(
        String code,
        String nameVi,
        String nameEn,
        Integer limit,
        long usage,
        boolean allowed) {
}

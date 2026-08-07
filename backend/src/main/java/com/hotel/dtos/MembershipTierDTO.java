package com.hotel.dtos;

import java.util.Map;

public record MembershipTierDTO(
        Long id,
        String ownerType,
        Long hotelId,
        String code,
        String nameVi,
        String nameEn,
        Integer rank,
        Map<String, Object> eligibility,
        Map<String, Object> benefits,
        String status) {
}


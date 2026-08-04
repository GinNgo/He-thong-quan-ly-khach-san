package com.hotel.dtos;

import java.time.LocalDateTime;

public record OperationalPolicyDTO(
        Long id,
        Long hotelId,
        Long version,
        String status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        String checkInVi,
        String checkInEn,
        String checkOutVi,
        String checkOutEn,
        String cancellationVi,
        String cancellationEn,
        String childPolicyVi,
        String childPolicyEn,
        String petPolicyVi,
        String petPolicyEn,
        String smokingPolicyVi,
        String smokingPolicyEn,
        String houseRulesVi,
        String houseRulesEn,
        Long rowVersion) {
}

package com.hotel.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record OperationalPolicyRequest(
        @NotNull @FutureOrPresent LocalDateTime effectiveFrom,
        @NotBlank @Size(max = 2000) String checkInVi,
        @Size(max = 2000) String checkInEn,
        @NotBlank @Size(max = 2000) String checkOutVi,
        @Size(max = 2000) String checkOutEn,
        @NotBlank @Size(max = 3000) String cancellationVi,
        @Size(max = 3000) String cancellationEn,
        @NotBlank @Size(max = 2000) String childPolicyVi,
        @Size(max = 2000) String childPolicyEn,
        @NotBlank @Size(max = 2000) String petPolicyVi,
        @Size(max = 2000) String petPolicyEn,
        @NotBlank @Size(max = 2000) String smokingPolicyVi,
        @Size(max = 2000) String smokingPolicyEn,
        @NotBlank @Size(max = 4000) String houseRulesVi,
        @Size(max = 4000) String houseRulesEn) {
}

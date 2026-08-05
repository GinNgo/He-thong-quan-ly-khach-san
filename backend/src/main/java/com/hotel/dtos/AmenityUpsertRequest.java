package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AmenityUpsertRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String nameVi,
        @Size(max = 255) String nameEn,
        @NotBlank @Size(max = 30) String category,
        @Size(max = 100) String icon,
        @NotNull @PositiveOrZero Integer sortOrder) {
}

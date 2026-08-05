package com.hotel.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PropertyProfileUpdateRequest(
        @NotNull @Valid PropertyProfileDTO profile,
        @NotBlank @Size(min = 3, max = 500) String reason) {
}

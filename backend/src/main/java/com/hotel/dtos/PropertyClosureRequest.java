package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PropertyClosureRequest(
        @NotBlank @Size(min = 5, max = 500) String reason) {
}

package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCancellationRequest(
        @NotBlank @Size(max = 50) String reasonCode,
        @Size(max = 500) String reason) {
}

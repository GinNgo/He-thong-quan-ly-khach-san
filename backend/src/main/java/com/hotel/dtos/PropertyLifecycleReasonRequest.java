package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PropertyLifecycleReasonRequest(
        @NotBlank(message = "Lifecycle reason is required.")
        @Size(min = 10, max = 500, message = "Lifecycle reason must contain between 10 and 500 characters.")
        String reason) {

    public PropertyLifecycleReasonRequest {
        if (reason != null) {
            reason = reason.trim();
        }
    }
}

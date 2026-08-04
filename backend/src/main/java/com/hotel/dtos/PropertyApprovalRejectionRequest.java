package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PropertyApprovalRejectionRequest(
        @NotBlank(message = "Rejection reason is required.")
        @Size(min = 10, max = 500, message = "Rejection reason must contain between 10 and 500 characters.")
        String reason) {

    public PropertyApprovalRejectionRequest {
        if (reason != null) {
            reason = reason.trim();
        }
    }
}

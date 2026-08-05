package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportConversationLifecycleRequest(
        @NotBlank(message = "Lifecycle reason is required.")
        @Size(max = 500, message = "Lifecycle reason must not exceed 500 characters.")
        String reason) {
}

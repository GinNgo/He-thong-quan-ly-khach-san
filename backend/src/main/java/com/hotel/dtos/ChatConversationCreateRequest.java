package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ChatConversationCreateRequest(
        @NotBlank @Size(max = 120) String subject,
        @Positive Long hotelId,
        @Positive Long reservationId) {
}

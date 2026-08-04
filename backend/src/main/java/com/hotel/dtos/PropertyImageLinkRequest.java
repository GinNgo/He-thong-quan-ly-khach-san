package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PropertyImageLinkRequest(
        @NotBlank @Size(max = 1000) String imageUrl,
        @NotBlank @Size(max = 255) String altTextVi,
        @Size(max = 255) String altTextEn,
        boolean primary) {
}

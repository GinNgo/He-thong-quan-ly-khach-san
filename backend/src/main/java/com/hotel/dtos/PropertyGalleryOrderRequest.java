package com.hotel.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PropertyGalleryOrderRequest(
        @NotEmpty List<@NotNull Long> imageIds) {
}

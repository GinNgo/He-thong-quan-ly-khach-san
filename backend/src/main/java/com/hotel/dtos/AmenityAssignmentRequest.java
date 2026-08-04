package com.hotel.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AmenityAssignmentRequest(
        @NotNull @Size(max = 100) List<@NotNull Long> amenityIds) {
}

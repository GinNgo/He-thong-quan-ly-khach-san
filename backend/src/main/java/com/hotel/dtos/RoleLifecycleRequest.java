package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleLifecycleRequest {

    @NotNull(message = "Expected version is required.")
    @PositiveOrZero(message = "Expected version is invalid.")
    private Long expectedVersion;

    @NotBlank(message = "Change reason is required.")
    @Size(min = 3, max = 500, message = "Change reason must contain between 3 and 500 characters.")
    private String reason;
}

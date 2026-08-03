package com.hotel.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class RoleCreateRequest {

    @NotBlank(message = "Role code is required.")
    @Size(max = 50, message = "Role code must not exceed 50 characters.")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "Role code must start with a letter and contain only letters, numbers or underscores.")
    private String code;

    @NotBlank(message = "Role name is required.")
    @Size(max = 100, message = "Role name must not exceed 100 characters.")
    private String name;

    @Size(max = 500, message = "Role description must not exceed 500 characters.")
    private String description;
}

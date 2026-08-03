package com.hotel.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerRegistrationRequest {

    @NotBlank
    @Email
    @Size(max = 320)
    private String email;

    @NotBlank
    @Size(min = 8, max = 256)
    private String password;

    @NotBlank
    @Size(max = 150)
    private String fullName;

    @NotBlank
    @Pattern(regexp = "^[0-9+() .-]{8,30}$", message = "Phone number format is invalid.")
    private String phone;

    @NotBlank
    @Size(max = 255)
    private String propertyName;

    @NotNull
    @Positive
    private Long provinceId;

    @NotNull
    @Positive
    private Long wardId;

    @NotBlank
    @Size(max = 1000)
    private String address;
}

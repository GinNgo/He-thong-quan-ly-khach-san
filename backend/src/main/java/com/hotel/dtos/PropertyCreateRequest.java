package com.hotel.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PropertyCreateRequest {
    @NotBlank
    @Size(max = 255)
    private String nameVi;

    @Size(max = 255)
    private String nameEn;

    @NotBlank
    @Size(max = 50)
    private String propertyType;

    @NotBlank
    @Size(max = 1000)
    private String addressLine;

    @NotNull
    private Long provinceId;

    @NotNull
    private Long wardId;

    @Size(max = 4000)
    private String descriptionVi;

    @Size(max = 4000)
    private String descriptionEn;

    @Min(0)
    @Max(5)
    private Integer starRating;

    @Size(max = 50)
    private String phone;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 1000)
    private String website;

    @Size(max = 1000)
    private String mainImage;
}

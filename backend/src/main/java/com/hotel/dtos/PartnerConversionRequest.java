package com.hotel.dtos;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerConversionRequest {

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

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("Unknown conversion field: " + field);
    }
}

package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePaymentSessionRequest {

    @NotNull
    private Long reservationId;

    @NotBlank
    private String provider;
}

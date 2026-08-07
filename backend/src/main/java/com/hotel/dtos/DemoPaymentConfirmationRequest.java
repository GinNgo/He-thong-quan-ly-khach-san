package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DemoPaymentConfirmationRequest {

    @NotBlank
    private String token;
}

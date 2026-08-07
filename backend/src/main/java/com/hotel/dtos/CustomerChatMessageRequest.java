package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerChatMessageRequest {

    @NotBlank
    @Size(max = 2000)
    private String content;

    @Positive
    private Long hotelId;

    @Positive
    private Long reservationId;
}

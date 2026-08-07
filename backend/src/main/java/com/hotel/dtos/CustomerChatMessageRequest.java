package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CustomerChatMessageRequest {

    @Positive
    private Long conversationId;

    @Positive
    private Long hotelId;

    @Positive
    private Long reservationId;

    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9._:-]+")
    private String clientMessageId;

    @NotBlank
    @Size(max = 2000)
    private String content;

    @Positive
    private Long hotelId;

    @Positive
    private Long reservationId;
}

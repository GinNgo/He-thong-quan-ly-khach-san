package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CustomerChatMessageRequest {

    @Positive
    private Long conversationId;

    @NotBlank
    @Size(max = 2000)
    private String content;
}

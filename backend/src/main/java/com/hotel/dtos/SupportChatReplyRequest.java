package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupportChatReplyRequest {

    @Positive
    private Long customerId;

    @Positive
    private Long conversationId;

    @NotBlank
    @Size(max = 2000)
    private String content;
}

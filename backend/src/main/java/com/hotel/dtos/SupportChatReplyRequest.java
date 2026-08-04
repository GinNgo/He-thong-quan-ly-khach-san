package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupportChatReplyRequest {

    @Positive
    private Long customerId;

    @NotNull
    @Positive
    private Long conversationId;

    @PositiveOrZero
    private Long expectedVersion;

    @NotBlank
    @Size(max = 2000)
    private String content;
}

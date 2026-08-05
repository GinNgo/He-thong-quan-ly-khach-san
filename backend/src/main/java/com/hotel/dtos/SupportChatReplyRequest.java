package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
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

    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9._:-]+")
    private String clientMessageId;

    @NotBlank
    @Size(max = 2000)
    private String content;
}

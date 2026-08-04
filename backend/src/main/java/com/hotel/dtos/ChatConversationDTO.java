package com.hotel.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ChatConversationDTO {
    private Long conversationId;
    private Long customerId;
    private String customerName;
    private String subject;
    private String lastMessage;
    private Instant lastMessageAt;
}

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
    private Long hotelId;
    private String hotelName;
    private Long reservationId;
    private Long assignedAgentId;
    private String assignedAgentName;
    private String status;
    private Long version;
    private Instant slaDeadlineAt;
    private String slaState;
    private String lastMessage;
    private Instant lastMessageAt;
}

package com.hotel.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.Instant;

@Data
public class ChatMessageDTO {
    private Long id;
    private Long conversationId;
    private Long hotelId;
    private Long senderId;
    private Long receiverId;
    private Long conversationId;
    private Long hotelId;
    private String clientMessageId;
    private String content;
    private Instant timestamp;
    @JsonProperty("isRead")
    private boolean isRead;
    private String deliveryStatus;
    private Instant deliveredAt;
    private Instant readAt;
}

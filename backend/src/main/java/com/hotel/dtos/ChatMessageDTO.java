package com.hotel.dtos;

import lombok.Data;
import java.time.Instant;

@Data
public class ChatMessageDTO {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Instant timestamp;
    private boolean isRead;
}

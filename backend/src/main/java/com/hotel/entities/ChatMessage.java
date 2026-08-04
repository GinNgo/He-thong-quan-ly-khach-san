package com.hotel.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Always derived from the authenticated principal by the chat controller.
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    // Customer messages use receiverId=0 for the central SYSTEM.AI_CHAT queue;
    // support replies use the customer's user ID.
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "is_read")
    private boolean isRead;

    @PrePersist
    protected void onCreate() {
        this.timestamp = Instant.now();
        this.isRead = false;
    }
}

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

    // The user who sends the message (Could be Client or Admin)
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    // The intended recipient (Client or Admin). 
    // If Admin sends to Client, receiverId is Client's ID.
    // If Client sends to Admin, receiverId can be Admin's ID or 0 for a general inbox.
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

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

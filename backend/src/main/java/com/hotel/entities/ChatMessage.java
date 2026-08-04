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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", insertable = false, updatable = false)
    private SupportConversation conversation;

    @Column(name = "hotel_id")
    private Long hotelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", insertable = false, updatable = false)
    private Hotel hotel;

    @Column(name = "legacy_unscoped", nullable = false)
    private boolean legacyUnscoped;

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

    public void setConversation(SupportConversation conversation) {
        this.conversation = conversation;
        this.conversationId = conversation == null ? null : conversation.getId();
    }

    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
        this.hotelId = hotel == null ? null : hotel.getId();
    }
}

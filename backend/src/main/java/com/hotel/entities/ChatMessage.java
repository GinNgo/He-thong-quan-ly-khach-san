package com.hotel.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@FilterDef(name = "chatMessageTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "chatMessageTenantFilter", condition = "hotel_id = :hotelId")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private SupportConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(name = "legacy_unscoped", nullable = false)
    private boolean legacyUnscoped;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "is_read")
    private boolean isRead;

    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus = "SENT";

    @PrePersist
    protected void onCreate() {
        this.timestamp = Instant.now();
        this.isRead = false;
        if (this.deliveryStatus == null || this.deliveryStatus.isBlank()) {
            this.deliveryStatus = "SENT";
        }
    }
}

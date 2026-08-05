package com.hotel.notifications.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_delivery_outbox")
public class NotificationDeliveryOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, unique = true)
    private Long notificationId;

    @Column(name = "recipient_username", length = 190)
    private String recipientUsername;

    @Column(nullable = false, length = 160)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "last_error_type", length = 120)
    private String lastErrorType;

    @Version
    @Column(nullable = false)
    private long version;

    public static NotificationDeliveryOutbox pending(
            Long notificationId,
            String recipientUsername,
            String destination,
            LocalDateTime now) {
        NotificationDeliveryOutbox outbox = new NotificationDeliveryOutbox();
        outbox.notificationId = notificationId;
        outbox.recipientUsername = recipientUsername;
        outbox.destination = destination;
        outbox.status = NotificationDeliveryStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.nextAttemptAt = now;
        outbox.createdAt = now;
        return outbox;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public NotificationDeliveryStatus getStatus() { return status; }
    public void setStatus(NotificationDeliveryStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getLastErrorType() { return lastErrorType; }
    public void setLastErrorType(String lastErrorType) { this.lastErrorType = lastErrorType; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}

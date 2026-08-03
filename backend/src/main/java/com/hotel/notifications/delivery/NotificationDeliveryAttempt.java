package com.hotel.notifications.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_delivery_attempts")
public class NotificationDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_id", nullable = false)
    private Long outboxId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "error_type", length = 120)
    private String errorType;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    public static NotificationDeliveryAttempt of(
            Long outboxId,
            int attemptNumber,
            String outcome,
            String errorType,
            LocalDateTime attemptedAt) {
        NotificationDeliveryAttempt attempt = new NotificationDeliveryAttempt();
        attempt.outboxId = outboxId;
        attempt.attemptNumber = attemptNumber;
        attempt.outcome = outcome;
        attempt.errorType = errorType;
        attempt.attemptedAt = attemptedAt;
        return attempt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long outboxId) { this.outboxId = outboxId; }
    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
}

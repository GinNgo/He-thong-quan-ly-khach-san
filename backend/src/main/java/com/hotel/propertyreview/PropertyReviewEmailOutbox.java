package com.hotel.propertyreview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "property_review_email_outbox", indexes = {
        @Index(name = "IX_property_review_email_due", columnList = "status,next_attempt_at,id"),
        @Index(name = "IX_property_review_email_claim", columnList = "status,claimed_at,id"),
        @Index(name = "IX_property_review_email_property", columnList = "hotel_id,created_at,id")
}, uniqueConstraints = @UniqueConstraint(
        name = "UQ_property_review_email_event_recipient",
        columnNames = {"audit_event_id", "recipient_user_id"}))
public class PropertyReviewEmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_event_id", nullable = false)
    private Long auditEventId;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String subject;

    @Column(name = "body_text", nullable = false, columnDefinition = "nvarchar(max)")
    private String bodyText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PropertyReviewEmailStatus status = PropertyReviewEmailStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    public PropertyReviewEmailOutbox(
            Long auditEventId,
            Long hotelId,
            Long recipientUserId,
            String recipientEmail,
            String subject,
            String bodyText,
            int maxAttempts,
            LocalDateTime now) {
        this.auditEventId = auditEventId;
        this.hotelId = hotelId;
        this.recipientUserId = recipientUserId;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.bodyText = bodyText;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void claim(String token, LocalDateTime now) {
        status = PropertyReviewEmailStatus.PROCESSING;
        claimToken = token;
        claimedAt = now;
        updatedAt = now;
    }

    void markSent(LocalDateTime now) {
        attemptCount++;
        status = PropertyReviewEmailStatus.SENT;
        sentAt = now;
        lastErrorCode = null;
        clearClaim();
        updatedAt = now;
    }

    void markFailed(String errorCode, LocalDateTime nextAttempt, LocalDateTime now) {
        attemptCount++;
        status = attemptCount >= maxAttempts
                ? PropertyReviewEmailStatus.DEAD_LETTER
                : PropertyReviewEmailStatus.FAILED;
        lastErrorCode = errorCode;
        nextAttemptAt = nextAttempt;
        clearClaim();
        updatedAt = now;
    }

    void markDeadLetter(String errorCode, LocalDateTime now) {
        attemptCount++;
        status = PropertyReviewEmailStatus.DEAD_LETTER;
        lastErrorCode = errorCode;
        nextAttemptAt = now;
        clearClaim();
        updatedAt = now;
    }

    private void clearClaim() {
        claimToken = null;
        claimedAt = null;
    }
}

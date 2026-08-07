package com.hotel.emailoutbox;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "email_outbox", indexes = {
        @Index(name = "IX_email_outbox_due", columnList = "status,next_attempt_at,id"),
        @Index(name = "IX_email_outbox_failures", columnList = "status,failed_at,id"),
        @Index(name = "IX_email_outbox_hotel_status", columnList = "hotel_id,status,created_at")
})
public class EmailOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 180)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "template_key", nullable = false, length = 80)
    private String templateKey;

    @Column(name = "template_version", nullable = false, length = 40)
    private String templateVersion;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Lob
    @Basic(fetch = jakarta.persistence.FetchType.LAZY)
    @Column(name = "body_html")
    private String bodyHtml;

    @Lob
    @Basic(fetch = jakarta.persistence.FetchType.LAZY)
    @Column(name = "body_text")
    private String bodyText;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_content_type", length = 120)
    private String attachmentContentType;

    @Lob
    @Basic(fetch = jakarta.persistence.FetchType.LAZY)
    @Column(name = "attachment_bytes")
    private byte[] attachmentBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "manual_retry_count", nullable = false)
    private int manualRetryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "provider_message_id", length = 180)
    private String providerMessageId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    public EmailOutboxMessage(Long hotelId, String idempotencyKey, String requestHash,
                              String templateKey, String templateVersion, String recipientEmail,
                              String subject, String bodyHtml, String bodyText,
                              String attachmentName, String attachmentContentType, byte[] attachmentBytes,
                              int maxAttempts, LocalDateTime now) {
        this.hotelId = hotelId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.templateKey = templateKey;
        this.templateVersion = templateVersion;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.bodyHtml = bodyHtml;
        this.bodyText = bodyText;
        this.attachmentName = attachmentName;
        this.attachmentContentType = attachmentContentType;
        this.attachmentBytes = attachmentBytes;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markProcessing(LocalDateTime now) {
        this.status = EmailOutboxStatus.PROCESSING;
        this.updatedAt = now;
    }

    public void markSent(String providerMessageId, LocalDateTime now) {
        this.attemptCount++;
        this.status = EmailOutboxStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = now;
        this.failedAt = null;
        this.lastErrorCode = null;
        this.updatedAt = now;
    }

    public void markFailed(String errorCode, LocalDateTime nextAttemptAt, boolean terminal, LocalDateTime now) {
        this.attemptCount++;
        this.status = terminal ? EmailOutboxStatus.DEAD_LETTER : EmailOutboxStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.failedAt = now;
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = now;
    }

    public void markBounced(String errorCode, LocalDateTime now) {
        this.status = EmailOutboxStatus.BOUNCED;
        this.lastErrorCode = errorCode;
        this.failedAt = now;
        this.updatedAt = now;
    }

    public void manualRetry(LocalDateTime now) {
        this.status = EmailOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.manualRetryCount++;
        this.lastErrorCode = null;
        this.failedAt = null;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    public boolean isTerminal() {
        return status == EmailOutboxStatus.SENT || status == EmailOutboxStatus.BOUNCED
                || status == EmailOutboxStatus.DEAD_LETTER;
    }
}

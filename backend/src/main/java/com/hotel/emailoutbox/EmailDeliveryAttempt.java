package com.hotel.emailoutbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "email_delivery_attempts", uniqueConstraints = @UniqueConstraint(
        name = "UQ_email_delivery_attempt", columnNames = {"outbox_id", "attempt_number"}))
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
public class EmailDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "outbox_id", nullable = false)
    private EmailOutboxMessage outbox;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EmailDeliveryOutcome outcome;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "provider_message_id", length = 180)
    private String providerMessageId;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    protected EmailDeliveryAttempt() {
    }

    public EmailDeliveryAttempt(EmailOutboxMessage outbox, int attemptNumber,
                                EmailDeliveryOutcome outcome, String errorCode,
                                String providerMessageId, long durationMs, LocalDateTime attemptedAt) {
        this.outbox = outbox;
        this.attemptNumber = attemptNumber;
        this.outcome = outcome;
        this.errorCode = errorCode;
        this.providerMessageId = providerMessageId;
        this.durationMs = durationMs;
        this.attemptedAt = attemptedAt;
    }

    @jakarta.persistence.PreUpdate
    @jakarta.persistence.PreRemove
    void rejectMutation() {
        throw new IllegalStateException("Email delivery attempts are append-only");
    }
}

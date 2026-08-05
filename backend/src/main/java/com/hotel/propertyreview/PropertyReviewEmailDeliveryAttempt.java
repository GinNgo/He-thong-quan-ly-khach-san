package com.hotel.propertyreview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "property_review_email_delivery_attempts", uniqueConstraints = @UniqueConstraint(
        name = "UQ_property_review_email_attempt",
        columnNames = {"outbox_id", "attempt_number"}))
public class PropertyReviewEmailDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "outbox_id", nullable = false)
    private PropertyReviewEmailOutbox outbox;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PropertyReviewEmailOutcome outcome;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    protected PropertyReviewEmailDeliveryAttempt() {
    }

    public PropertyReviewEmailDeliveryAttempt(
            PropertyReviewEmailOutbox outbox,
            int attemptNumber,
            PropertyReviewEmailOutcome outcome,
            String errorCode,
            long durationMs,
            LocalDateTime attemptedAt) {
        if (outbox == null) {
            throw new IllegalArgumentException("Email outbox item is required.");
        }
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("Email delivery attempt number must be positive.");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Email delivery outcome is required.");
        }
        String normalizedError = errorCode == null ? null : errorCode.trim();
        if (outcome == PropertyReviewEmailOutcome.SENT && normalizedError != null) {
            throw new IllegalArgumentException("Successful email delivery cannot contain an error code.");
        }
        if (outcome == PropertyReviewEmailOutcome.FAILED
                && (normalizedError == null || normalizedError.isEmpty())) {
            throw new IllegalArgumentException("Failed email delivery requires an error code.");
        }
        if (normalizedError != null && normalizedError.length() > 80) {
            throw new IllegalArgumentException("Email delivery error code is too long.");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("Email delivery duration cannot be negative.");
        }
        if (attemptedAt == null) {
            throw new IllegalArgumentException("Email delivery timestamp is required.");
        }
        this.outbox = outbox;
        this.attemptNumber = attemptNumber;
        this.outcome = outcome;
        this.errorCode = normalizedError;
        this.durationMs = durationMs;
        this.attemptedAt = attemptedAt;
    }

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new IllegalStateException("Property review email attempts are append-only.");
    }
}

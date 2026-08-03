package com.hotel.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only, pseudonymized evidence for credential-login decisions. */
@Entity
@Table(name = "auth_login_attempts", indexes = {
        @Index(name = "IX_auth_login_account_time", columnList = "account_fingerprint,occurred_at"),
        @Index(name = "IX_auth_login_ip_time", columnList = "ip_fingerprint,occurred_at"),
        @Index(name = "IX_auth_login_correlation", columnList = "correlation_id,occurred_at")
})
public class AuthLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "account_fingerprint", nullable = false, length = 64)
    private String accountFingerprint;

    @Column(name = "ip_fingerprint", nullable = false, length = 64)
    private String ipFingerprint;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuthLoginAttempt() {
    }

    public AuthLoginAttempt(Long userId, String accountFingerprint, String ipFingerprint,
                            String outcome, String reasonCode, String correlationId, Instant occurredAt) {
        this.userId = userId;
        this.accountFingerprint = accountFingerprint;
        this.ipFingerprint = ipFingerprint;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getAccountFingerprint() { return accountFingerprint; }
    public String getIpFingerprint() { return ipFingerprint; }
    public String getOutcome() { return outcome; }
    public String getReasonCode() { return reasonCode; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new IllegalStateException("Authentication login attempts are append-only");
    }
}

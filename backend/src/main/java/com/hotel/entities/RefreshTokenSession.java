package com.hotel.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "auth_refresh_tokens", indexes = {
        @Index(name = "IX_auth_refresh_family", columnList = "family_id,status"),
        @Index(name = "IX_auth_refresh_user", columnList = "user_id,status,expires_at")
})
public class RefreshTokenSession {

    public static final String ACTIVE = "ACTIVE";
    public static final String ROTATED = "ROTATED";
    public static final String REVOKED = "REVOKED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "reuse_detected_at")
    private Instant reuseDetectedAt;

    @Column(name = "replaced_by_hash", length = 64)
    private String replacedByHash;

    @Column(name = "revocation_reason", length = 100)
    private String revocationReason;

    @Version
    @Column(nullable = false)
    private Long version;

    protected RefreshTokenSession() {
    }

    public RefreshTokenSession(User user, String familyId, String tokenHash,
                               Instant issuedAt, Instant expiresAt) {
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.status = ACTIVE;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void rotate(String replacementHash, Instant now) {
        status = ROTATED;
        rotatedAt = now;
        replacedByHash = replacementHash;
    }

    public void expire(Instant now) {
        status = EXPIRED;
        revokedAt = now;
        revocationReason = "EXPIRED";
    }

    public void recordReuse(Instant now) {
        reuseDetectedAt = now;
        revocationReason = "REUSE_DETECTED";
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getFamilyId() { return familyId; }
    public String getTokenHash() { return tokenHash; }
    public String getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRotatedAt() { return rotatedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getReuseDetectedAt() { return reuseDetectedAt; }
    public String getReplacedByHash() { return replacedByHash; }
    public String getRevocationReason() { return revocationReason; }
    public Long getVersion() { return version; }
}

package com.hotel.paymentprovider.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "financial_idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "UQ_financial_idempotency_identity",
                columnNames = {"context", "operation", "scope_key", "idempotency_key"}),
        indexes = @Index(name = "IX_financial_idempotency_scope", columnList = "context,scope_key,operation,created_at"))
public class FinancialIdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String context;

    @Column(nullable = false, length = 80)
    private String operation;

    @Column(name = "scope_key", nullable = false, length = 160)
    private String scopeKey;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "nvarchar(max)")
    private String responseBody;

    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected FinancialIdempotencyRecord() {
    }

    FinancialIdempotencyRecord(String context, String operation, String scopeKey, String idempotencyKey,
                               String requestHash, Long hotelId, Long ownerUserId, String correlationId,
                               LocalDateTime createdAt) {
        this.context = context;
        this.operation = operation;
        this.scopeKey = scopeKey;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.hotelId = hotelId;
        this.ownerUserId = ownerUserId;
        this.correlationId = correlationId;
        this.state = "IN_PROGRESS";
        this.createdAt = createdAt;
    }

    void complete(int status, String body, LocalDateTime completedAt) {
        this.state = "COMPLETED";
        this.responseStatus = status;
        this.responseBody = body;
        this.completedAt = completedAt;
    }

    void fail(LocalDateTime completedAt) {
        this.state = "FAILED";
        this.completedAt = completedAt;
    }
}

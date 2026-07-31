package com.hotel.paymentprovider.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "financial_audit_events",
        indexes = {
                @Index(name = "IX_financial_audit_aggregate", columnList = "context,aggregate_type,aggregate_id,occurred_at"),
                @Index(name = "IX_financial_audit_correlation", columnList = "correlation_id,occurred_at")
        })
@EntityListeners(AuditingEntityListener.class)
@FilterDef(name = "financialAuditTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "financialAuditTenantFilter", condition = "hotel_id = :hotelId")
public class FinancialAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String context;

    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "previous_state", length = 50)
    private String previousState;

    @Column(name = "new_state", length = 50)
    private String newState;

    @Column(length = 1000, columnDefinition = "nvarchar(1000)")
    private String reason;

    @Column(name = "idempotency_identity", length = 200)
    private String idempotencyIdentity;

    @Column(name = "provider_identity", length = 200)
    private String providerIdentity;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "metadata_json", columnDefinition = "nvarchar(max)")
    private String metadataJson;

    @CreatedDate
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected FinancialAuditEvent() {
    }

    FinancialAuditEvent(String context, Long hotelId, String aggregateType, String aggregateId,
                        String actorType, Long actorId, String source, String previousState,
                        String newState, String reason, String idempotencyIdentity,
                        String providerIdentity, String correlationId, String metadataJson,
                        LocalDateTime occurredAt) {
        this.context = context;
        this.hotelId = hotelId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.source = source;
        this.previousState = previousState;
        this.newState = newState;
        this.reason = reason;
        this.idempotencyIdentity = idempotencyIdentity;
        this.providerIdentity = providerIdentity;
        this.correlationId = correlationId;
        this.metadataJson = metadataJson;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    void rejectMutation() {
        throw new IllegalStateException("Financial audit events are append-only");
    }
}

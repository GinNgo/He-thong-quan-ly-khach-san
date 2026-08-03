package com.hotel.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/** Append-only operational evidence shared by tenant and system administration flows. */
@Getter
@Entity
@Table(name = "operational_audit_events", indexes = {
        @Index(name = "IX_operational_audit_tenant_time", columnList = "scope,hotel_id,occurred_at,id"),
        @Index(name = "IX_operational_audit_aggregate", columnList = "aggregate_type,aggregate_id,occurred_at"),
        @Index(name = "IX_operational_audit_correlation", columnList = "correlation_id,occurred_at")
})
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
public class OperationalAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(nullable = false, length = 40)
    private String domain;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String reason;

    @Column(name = "before_state_json", columnDefinition = "nvarchar(max)")
    private String beforeStateJson;

    @Column(name = "after_state_json", columnDefinition = "nvarchar(max)")
    private String afterStateJson;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected OperationalAuditEvent() {
    }

    public OperationalAuditEvent(String scope, Long hotelId, String domain, String eventType,
                                 String aggregateType, String aggregateId, String actorType,
                                 Long actorId, String reason, String beforeStateJson,
                                 String afterStateJson, String correlationId, LocalDateTime occurredAt) {
        this.scope = scope;
        this.hotelId = hotelId;
        this.domain = domain;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.reason = reason;
        this.beforeStateJson = beforeStateJson;
        this.afterStateJson = afterStateJson;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new IllegalStateException("Operational audit events are append-only");
    }
}

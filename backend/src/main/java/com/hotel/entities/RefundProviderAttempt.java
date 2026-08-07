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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refund_provider_attempts",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_refund_attempts_request_number", columnNames = {"refund_request_id", "attempt_number"}),
                @UniqueConstraint(name = "UQ_refund_attempts_idempotency", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "IX_refund_attempts_request_status", columnList = "refund_request_id,status"),
                @Index(name = "IX_refund_attempts_hotel_status", columnList = "hotel_id,status")
        })
@FilterDef(name = "refundAttemptTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "refundAttemptTenantFilter", condition = "hotel_id = :hotelId")
public class RefundProviderAttempt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_request_id", nullable = false)
    private RefundRequest refundRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;

    @Column(name = "requested_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal requestedAmount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "provider_reference", length = 160)
    private String providerReference;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "response_code", length = 80)
    private String responseCode;

    @Column(name = "details_json", columnDefinition = "nvarchar(max)")
    private String detailsJson;
}

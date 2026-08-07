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
@Table(name = "refund_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_refund_requests_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "UQ_refund_requests_original_payment", columnNames = "original_payment_id"),
                @UniqueConstraint(name = "UQ_refund_requests_idempotency", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "IX_refund_requests_reservation_status", columnList = "reservation_id,status"),
                @Index(name = "IX_refund_requests_hotel_status", columnList = "hotel_id,status")
        })
@FilterDef(name = "refundRequestTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "refundRequestTenantFilter", condition = "hotel_id = :hotelId")
public class RefundRequest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_payment_id", nullable = false)
    private Payment originalPayment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "requested_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal requestedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(columnDefinition = "nvarchar(500)")
    private String reason;

    @Column(name = "provider_refund_reference", length = 160)
    private String providerRefundReference;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "points_reversed_at")
    private LocalDateTime pointsReversedAt;

    @Column(name = "request_notified_at")
    private LocalDateTime requestNotifiedAt;

    @Column(name = "terminal_notified_at")
    private LocalDateTime terminalNotifiedAt;

    @Column(name = "failure_code", length = 80)
    private String failureCode;
}

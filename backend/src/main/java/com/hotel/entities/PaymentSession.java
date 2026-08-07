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
@Table(name = "payment_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_payment_sessions_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "UQ_payment_sessions_provider_reference", columnNames = "provider_reference"),
                @UniqueConstraint(name = "UQ_payment_sessions_owner_idempotency", columnNames = {"owner_user_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "IX_payment_sessions_reservation_status", columnList = "reservation_id,status"),
                @Index(name = "IX_payment_sessions_hotel_status", columnList = "hotel_id,status"),
                @Index(name = "IX_payment_sessions_expiry", columnList = "status,expires_at")
        })
@FilterDef(name = "paymentSessionTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "paymentSessionTenantFilter", condition = "hotel_id = :hotelId")
public class PaymentSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 40)
    private String method;

    @Column(name = "expected_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_reference", nullable = false, length = 120)
    private String providerReference;

    @Column(name = "provider_transaction_id", length = 160)
    private String providerTransactionId;

    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "reconciliation_required", nullable = false)
    private boolean reconciliationRequired;

    @Column(name = "failure_code", length = 80)
    private String failureCode;
}

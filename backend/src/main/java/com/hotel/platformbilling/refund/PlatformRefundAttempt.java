package com.hotel.platformbilling.refund;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "platform_refund_attempts", uniqueConstraints = @UniqueConstraint(
        name = "UQ_platform_refund_attempt", columnNames = {"refund_request_id", "attempt_number"}))
public class PlatformRefundAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_request_id", nullable = false, updatable = false)
    private PlatformRefundRequest refundRequest;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private Integer attemptNumber;

    @Column(nullable = false, length = 40, updatable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentEnvironment environment;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "provider_event_id", length = 200)
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundState status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected PlatformRefundAttempt() {
    }

    public static PlatformRefundAttempt create(
            PlatformRefundRequest request,
            int attemptNumber,
            String provider,
            PaymentEnvironment environment,
            LocalDateTime requestedAt) {
        if (attemptNumber < 1) throw new IllegalArgumentException("attemptNumber must be positive.");
        PlatformRefundAttempt attempt = new PlatformRefundAttempt();
        attempt.refundRequest = request;
        attempt.attemptNumber = attemptNumber;
        attempt.provider = provider;
        attempt.environment = environment;
        attempt.status = RefundState.REQUESTED;
        attempt.requestedAt = requestedAt;
        return attempt;
    }
}

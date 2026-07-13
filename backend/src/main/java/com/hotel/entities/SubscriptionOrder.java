package com.hotel.entities;

import lombok.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "subscription_orders")
public class SubscriptionOrder extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", unique = true, nullable = false)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "billing_type")
    private String billingType; // YEARLY, LIFETIME

    @Column(name = "duration_value")
    private Integer durationValue;

    private BigDecimal subtotal;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    private String currency = "VND";

    private String status = "DRAFT"; // DRAFT, PENDING_PAYMENT, PAYMENT_REVIEW, PAID, ACTIVATED, CANCELLED, EXPIRED, REFUNDED

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}

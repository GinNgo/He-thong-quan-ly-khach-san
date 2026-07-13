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
@Table(name = "subscription_payments")
public class SubscriptionPayment extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private SubscriptionOrder order;

    @Column(name = "payment_method")
    private String paymentMethod; // VNPAY, MOMO, BANK_TRANSFER, CASH

    private BigDecimal amount;

    @Column(name = "transaction_code")
    private String transactionCode;

    @Column(name = "payment_status")
    private String paymentStatus = "PENDING"; // PENDING, SUCCESS, FAILED, REJECTED, REFUNDED

    @Column(name = "payment_proof_url")
    private String paymentProofUrl;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verification_note", columnDefinition = "NVARCHAR(MAX)")
    private String verificationNote;
}

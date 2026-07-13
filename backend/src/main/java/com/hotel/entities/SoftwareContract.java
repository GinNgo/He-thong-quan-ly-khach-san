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
@Table(name = "software_contracts")
public class SoftwareContract extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_no", unique = true, nullable = false)
    private String contractNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Hotel property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private SubscriptionOrder order;

    @Column(name = "contract_type")
    private String contractType; // YEARLY_RENTAL, LIFETIME_PURCHASE, CUSTOM_IMPLEMENTATION

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "is_lifetime")
    private Boolean isLifetime = false;

    @Column(name = "contract_value")
    private BigDecimal contractValue;

    private String currency = "VND";

    private String status = "DRAFT"; // DRAFT, PENDING_SIGNATURE, ACTIVE, EXPIRED, TERMINATED, CANCELLED

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "document_url")
    private String documentUrl;
}

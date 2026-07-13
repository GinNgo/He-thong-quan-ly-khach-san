package com.hotel.entities;

import lombok.*;
import jakarta.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "subscription_histories")
public class SubscriptionHistory extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_subscription_id", nullable = false)
    private AccountSubscription accountSubscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "action_type")
    private String actionType; // ACTIVATED, RENEWED, UPGRADED, CANCELLED, EXPIRED

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String note;
}

package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "name_vi", nullable = false)
    private String nameVi;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "billing_type", nullable = false)
    private String billingType; // MONTHLY, YEARLY, ONCE

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "is_lifetime")
    private Boolean isLifetime = false;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PlanFeature> features;
}

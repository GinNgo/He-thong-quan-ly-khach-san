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

    @Column(name = "family_code", nullable = false, length = 50)
    private String familyCode;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber = 1;

    @Column(name = "name_vi", nullable = false, columnDefinition = "nvarchar(255)")
    private String nameVi;

    @Column(name = "name_en", columnDefinition = "nvarchar(255)")
    private String nameEn;

    @Column(name = "billing_type", nullable = false)
    private String billingType; // MONTHLY, YEARLY, ONCE

    @Column(name = "duration_value")
    private Integer durationValue;

    @Column(name = "duration_unit", length = 20)
    private String durationUnit;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "is_lifetime")
    private Boolean isLifetime = false;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;

    @Column(name = "activated_at")
    private java.time.LocalDateTime activatedAt;

    @Column(name = "deactivated_at")
    private java.time.LocalDateTime deactivatedAt;

    @Column(name = "creation_key_hash", length = 64, updatable = false)
    private String creationKeyHash;

    @Column(name = "creation_payload_hash", length = 64, updatable = false)
    private String creationPayloadHash;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PlanFeature> features;
}

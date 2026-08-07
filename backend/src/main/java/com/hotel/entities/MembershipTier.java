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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership_tiers",
        uniqueConstraints = @UniqueConstraint(name = "UX_membership_tiers_scope_code", columnNames = {"owner_type", "hotel_id", "code"}),
        indexes = @Index(name = "IX_membership_tiers_hotel_status_rank", columnList = "hotel_id,status,tier_rank"))
@FilterDef(name = "membershipTierTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "membershipTierTenantFilter", condition = "hotel_id = :hotelId OR hotel_id IS NULL")
public class MembershipTier extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 255, columnDefinition = "nvarchar(255)")
    private String nameVi;

    @Column(name = "name_en", length = 255, columnDefinition = "nvarchar(255)")
    private String nameEn;

    @Column(name = "tier_rank", nullable = false)
    private Integer tierRank;

    @Column(name = "eligibility_json", columnDefinition = "nvarchar(max)")
    private String eligibilityJson;

    @Column(name = "benefits_json", columnDefinition = "nvarchar(max)")
    private String benefitsJson;

    @Column(nullable = false, length = 20)
    private String status;
}


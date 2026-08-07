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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promotion_campaigns",
        uniqueConstraints = @UniqueConstraint(name = "UX_promotion_campaigns_scope_code", columnNames = {"owner_type", "hotel_id", "code"}),
        indexes = @Index(name = "IX_promotion_campaigns_hotel_status_time", columnList = "hotel_id,status,starts_at,ends_at"))
@FilterDef(name = "promotionCampaignTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "promotionCampaignTenantFilter", condition = "hotel_id = :hotelId OR hotel_id IS NULL")
public class PromotionCampaign extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(name = "application_type", nullable = false, length = 20)
    private String applicationType;

    @Column(name = "name_vi", nullable = false, length = 255, columnDefinition = "nvarchar(255)")
    private String nameVi;

    @Column(name = "name_en", length = 255, columnDefinition = "nvarchar(255)")
    private String nameEn;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount", precision = 19, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "eligibility_json", columnDefinition = "nvarchar(max)")
    private String eligibilityJson;

    @Column(precision = 19, scale = 2)
    private BigDecimal budget;

    @Column(name = "redemption_limit")
    private Long redemptionLimit;

    @Column(name = "per_customer_limit")
    private Long perCustomerLimit;

    @Column(name = "stacking_policy", nullable = false, length = 30)
    private String stackingPolicy;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(nullable = false, length = 20)
    private String status;

    @Version
    @Column(nullable = false)
    private Long version;
}


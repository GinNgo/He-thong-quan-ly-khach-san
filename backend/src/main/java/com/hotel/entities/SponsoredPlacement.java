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
@Table(name = "sponsored_placements", indexes = {
        @Index(name = "IX_sponsored_placements_public", columnList = "placement_surface,status,starts_at,ends_at,sort_priority"),
        @Index(name = "IX_sponsored_placements_hotel_status", columnList = "hotel_id,status")
})
@FilterDef(name = "sponsoredPlacementTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "sponsoredPlacementTenantFilter", condition = "hotel_id = :hotelId OR hotel_id IS NULL")
public class SponsoredPlacement extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(name = "placement_surface", nullable = false, length = 40)
    private String placementSurface;

    @Column(name = "placement_kind", nullable = false, length = 20)
    private String placementKind;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "title_vi", nullable = false, length = 255, columnDefinition = "nvarchar(255)")
    private String titleVi;

    @Column(name = "title_en", nullable = false, length = 255, columnDefinition = "nvarchar(255)")
    private String titleEn;

    @Column(name = "description_vi", length = 1000, columnDefinition = "nvarchar(1000)")
    private String descriptionVi;

    @Column(name = "description_en", length = 1000, columnDefinition = "nvarchar(1000)")
    private String descriptionEn;

    @Column(name = "image_url", nullable = false, length = 1000, columnDefinition = "nvarchar(1000)")
    private String imageUrl;

    @Column(name = "image_alt_vi", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String imageAltVi;

    @Column(name = "image_alt_en", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String imageAltEn;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_hotel_id")
    private Hotel targetHotel;

    @Column(name = "target_query_json", columnDefinition = "nvarchar(max)")
    private String targetQueryJson;

    @Column(name = "target_province_id")
    private Long targetProvinceId;

    @Column(name = "target_landmark_id")
    private Long targetLandmarkId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "sort_priority", nullable = false)
    private Integer sortPriority = 0;

    @Column(precision = 19, scale = 2)
    private BigDecimal budget;

    @Column(name = "spent_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal spentAmount = BigDecimal.ZERO;

    @Column(name = "impression_limit")
    private Long impressionLimit;

    @Column(name = "impression_count", nullable = false)
    private Long impressionCount = 0L;

    @Column(name = "click_limit")
    private Long clickLimit;

    @Column(name = "click_count", nullable = false)
    private Long clickCount = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_reason", length = 500, columnDefinition = "nvarchar(500)")
    private String rejectedReason;

    @Version
    @Column(nullable = false)
    private Long version;
}


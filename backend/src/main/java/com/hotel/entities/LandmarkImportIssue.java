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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "landmark_import_issues", indexes = {
        @Index(name = "IX_landmark_import_issues_run_review", columnList = "run_id,review_status"),
        @Index(name = "IX_landmark_import_issues_source_key", columnList = "source_provider,source_object_type,source_object_id")
})
public class LandmarkImportIssue extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private LocationImportRun run;

    @Column(name = "source_provider", length = 80)
    private String sourceProvider;

    @Column(name = "source_object_type", length = 50)
    private String sourceObjectType;

    @Column(name = "source_object_id", length = 255)
    private String sourceObjectId;

    @Column(name = "reason_code", nullable = false, length = 80)
    private String reasonCode;

    @Column(name = "raw_name", columnDefinition = "nvarchar(255)")
    private String rawName;

    @Column(name = "normalized_name", columnDefinition = "nvarchar(255)")
    private String normalizedName;

    @Column(name = "proposed_province", length = 120)
    private String proposedProvince;

    @Column(name = "proposed_latitude", precision = 9, scale = 6)
    private BigDecimal proposedLatitude;

    @Column(name = "proposed_longitude", precision = 9, scale = 6)
    private BigDecimal proposedLongitude;

    @Column(name = "match_score", precision = 6, scale = 5)
    private BigDecimal matchScore;

    @Column(name = "review_status", nullable = false, length = 24)
    private String reviewStatus;

    @Column(name = "details_json", columnDefinition = "nvarchar(max)")
    private String detailsJson;
}

package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "locations", indexes = {
        @Index(name = "IX_locations_type_parent_status", columnList = "location_type,parent_id,status"),
        @Index(name = "IX_locations_normalized_name", columnList = "normalized_name")
})
public class Location extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Location parent;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "source_code")
    private String sourceCode;

    @Column(name = "name_vi", nullable = false, columnDefinition = "nvarchar(255)")
    private String nameVi;

    @Column(name = "name_en", columnDefinition = "nvarchar(255)")
    private String nameEn;

    @Column(name = "normalized_name", columnDefinition = "nvarchar(255)")
    private String normalizedName;

    @Column(name = "location_type", nullable = false)
    private String locationType; // PROVINCE, WARD, LANDMARK

    @Column(name = "full_path", columnDefinition = "nvarchar(1000)")
    private String fullPath;

    @Column(name = "legacy_parent_name", columnDefinition = "nvarchar(255)")
    private String legacyParentName;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(unique = true)
    private String slug;

    @PrePersist
    @PreUpdate
    void normalizeSearchFields() {
        normalizedName = com.hotel.util.VietnameseTextNormalizer.normalize(nameVi);
    }
}

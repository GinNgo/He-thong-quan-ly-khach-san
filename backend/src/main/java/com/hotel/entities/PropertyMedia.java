package com.hotel.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "property_media")
@org.hibernate.annotations.FilterDef(name = "propertyMediaTenantFilter", parameters = @org.hibernate.annotations.ParamDef(name = "hotelId", type = Long.class))
@org.hibernate.annotations.Filter(name = "propertyMediaTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyMedia extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    @JsonIgnore
    private Hotel hotel;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "public_url", nullable = false, columnDefinition = "nvarchar(1000)")
    private String publicUrl;

    @Column(name = "storage_key", length = 255)
    private String storageKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "alt_text_vi", columnDefinition = "nvarchar(255)")
    private String altTextVi;

    @Column(name = "alt_text_en", columnDefinition = "nvarchar(255)")
    private String altTextEn;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "is_demo", nullable = false)
    private Boolean isDemo = false;

    public boolean isManagedUpload() {
        return "MANAGED_UPLOAD".equals(sourceType);
    }
}

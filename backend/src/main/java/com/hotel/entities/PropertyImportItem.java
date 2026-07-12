package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "property_import_items")
public class PropertyImportItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private PropertyImportBatch batch;

    @Column(name = "external_provider", nullable = false)
    private String externalProvider;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "raw_name", nullable = false)
    private String rawName;

    @Column(name = "normalized_name")
    private String normalizedName;

    @Column(name = "raw_address")
    private String rawAddress;

    @Column(name = "province_id")
    private Long provinceId;

    @Column(name = "ward_id")
    private Long wardId;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "phone")
    private String phone;

    @Column(name = "website")
    private String website;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "raw_payload_json", columnDefinition = "TEXT")
    private String rawPayloadJson;

    @Column(name = "duplicate_status")
    private String duplicateStatus; // NEW, EXACT_DUPLICATE, POSSIBLE_DUPLICATE, INVALID

    @Column(name = "duplicate_property_id")
    private Long duplicatePropertyId;

    @Column(name = "validation_status")
    private String validationStatus;

    @Column(name = "selected")
    private Boolean selected = false;

    @Column(name = "import_status")
    private String importStatus = "PENDING"; // PENDING, IMPORTED, FAILED, IGNORED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PropertyImportBatch getBatch() { return batch; }
    public void setBatch(PropertyImportBatch batch) { this.batch = batch; }
    public String getExternalProvider() { return externalProvider; }
    public void setExternalProvider(String externalProvider) { this.externalProvider = externalProvider; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getRawName() { return rawName; }
    public void setRawName(String rawName) { this.rawName = rawName; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getRawAddress() { return rawAddress; }
    public void setRawAddress(String rawAddress) { this.rawAddress = rawAddress; }
    public Long getProvinceId() { return provinceId; }
    public void setProvinceId(Long provinceId) { this.provinceId = provinceId; }
    public Long getWardId() { return wardId; }
    public void setWardId(Long wardId) { this.wardId = wardId; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getRawPayloadJson() { return rawPayloadJson; }
    public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
    public String getDuplicateStatus() { return duplicateStatus; }
    public void setDuplicateStatus(String duplicateStatus) { this.duplicateStatus = duplicateStatus; }
    public Long getDuplicatePropertyId() { return duplicatePropertyId; }
    public void setDuplicatePropertyId(Long duplicatePropertyId) { this.duplicatePropertyId = duplicatePropertyId; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public Boolean getSelected() { return selected; }
    public void setSelected(Boolean selected) { this.selected = selected; }
    public String getImportStatus() { return importStatus; }
    public void setImportStatus(String importStatus) { this.importStatus = importStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

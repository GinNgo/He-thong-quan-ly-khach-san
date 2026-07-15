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
@Table(name = "hotels", indexes = {
        @Index(name = "IX_hotels_location_status", columnList = "province_id,ward_id,approval_status,operation_status"),
        @Index(name = "IX_hotels_normalized_name", columnList = "normalized_name")
})
public class Hotel extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "nvarchar(255)")
    private String name;

    @Column(name = "name_vi", columnDefinition = "nvarchar(255)")
    private String nameVi;

    @Column(name = "name_en", columnDefinition = "nvarchar(255)")
    private String nameEn;

    @Column(name = "normalized_name", columnDefinition = "nvarchar(255)")
    private String normalizedName;

    @Column(unique = true)
    private String code;

    @Column(unique = true)
    private String slug;

    @Column(columnDefinition = "nvarchar(max)")
    private String description;

    @Column(name = "description_vi", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionVi;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "address", nullable = false, columnDefinition = "nvarchar(1000)")
    private String addressLine;

    @Column(name = "normalized_address", columnDefinition = "nvarchar(1000)")
    private String normalizedAddress;

    @Column(nullable = false, columnDefinition = "nvarchar(255)")
    private String city;

    @Column(nullable = false, columnDefinition = "nvarchar(255)")
    private String country;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "star_rating")
    private Integer starRating;

    @Column(name = "main_image")
    private String mainImage;

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT, PENDING_APPROVAL, ACTIVE, INACTIVE, SUSPENDED, CLOSED (Operation Status)

    @Column(name = "operation_status")
    private String operationStatus = "ACTIVE"; 

    @Column(name = "province_id")
    private Long provinceId;

    @Column(name = "ward_id")
    private Long wardId;

    @Column(name = "approval_status")
    private String approvalStatus = "DRAFT"; // DRAFT, PENDING_APPROVAL, APPROVED, REJECTED

    @Column(name = "external_provider")
    private String externalProvider;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "property_type")
    private String propertyType = "HOTEL"; 

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "website")
    private String website;

    @Column(name = "min_price")
    private Double minPrice;

    @Column(name = "max_price")
    private Double maxPrice;

    @Column(name = "checkin_time")
    private String checkinTime;

    @Column(name = "checkout_time")
    private String checkoutTime;

    @Column(name = "average_rating")
    private Double averageRating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "is_demo", nullable = false)
    private Boolean isDemo = false;

    @Column(name = "data_source", columnDefinition = "nvarchar(50)")
    private String dataSource;

    @Column(name = "seed_key", columnDefinition = "nvarchar(255)")
    private String seedKey;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<PropertyImage> images;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<UserProperty> userProperties;

    @PrePersist
    @PreUpdate
    void normalizeSearchFields() {
        String displayName = nameVi != null && !nameVi.isBlank() ? nameVi :
                (name != null && !name.isBlank() ? name : nameEn);
        normalizedName = com.hotel.util.VietnameseTextNormalizer.normalize(displayName);
        normalizedAddress = com.hotel.util.VietnameseTextNormalizer.joinAndNormalize(addressLine, city, country);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getStarRating() {
        return starRating;
    }

    public void setStarRating(Integer starRating) {
        this.starRating = starRating;
    }

    public String getMainImage() {
        return mainImage;
    }

    public void setMainImage(String mainImage) {
        this.mainImage = mainImage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProvinceId() {
        return provinceId;
    }

    public void setProvinceId(Long provinceId) {
        this.provinceId = provinceId;
    }

    public Long getWardId() {
        return wardId;
    }

    public void setWardId(Long wardId) {
        this.wardId = wardId;
    }

    public java.util.Set<PropertyImage> getImages() {
        return images;
    }

    public void setImages(java.util.Set<PropertyImage> images) {
        this.images = images;
    }

    public java.util.Set<UserProperty> getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(java.util.Set<UserProperty> userProperties) {
        this.userProperties = userProperties;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getExternalProvider() {
        return externalProvider;
    }

    public void setExternalProvider(String externalProvider) {
        this.externalProvider = externalProvider;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(String propertyType) {
        this.propertyType = propertyType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getOperationStatus() {
        return operationStatus;
    }

    public void setOperationStatus(String operationStatus) {
        this.operationStatus = operationStatus;
    }
}

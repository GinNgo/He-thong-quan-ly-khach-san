package com.hotel.dtos;

import java.time.LocalDateTime;

public class RoomImageDTO {
    private Long id;
    private String imageUrl;
    private Boolean isPrimary;
    private Integer sortOrder;
    private String altTextVi;
    private String altTextEn;
    private Long mediaId;
    private String sourceType;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getAltTextVi() { return altTextVi; }
    public void setAltTextVi(String altTextVi) { this.altTextVi = altTextVi; }
    public String getAltTextEn() { return altTextEn; }
    public void setAltTextEn(String altTextEn) { this.altTextEn = altTextEn; }
    public Long getMediaId() { return mediaId; }
    public void setMediaId(Long mediaId) { this.mediaId = mediaId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

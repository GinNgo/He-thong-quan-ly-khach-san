package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "property_external_photos")
public class PropertyExternalPhoto extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Hotel property;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "external_photo_id")
    private String externalPhotoId;

    @Column(name = "photo_reference", columnDefinition = "TEXT")
    private String photoReference;

    @Column(name = "display_url", columnDefinition = "TEXT")
    private String displayUrl;

    @Column(name = "attribution_text", columnDefinition = "TEXT")
    private String attributionText;

    @Column(name = "attribution_url", columnDefinition = "TEXT")
    private String attributionUrl;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Hotel getProperty() { return property; }
    public void setProperty(Hotel property) { this.property = property; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalPhotoId() { return externalPhotoId; }
    public void setExternalPhotoId(String externalPhotoId) { this.externalPhotoId = externalPhotoId; }
    public String getPhotoReference() { return photoReference; }
    public void setPhotoReference(String photoReference) { this.photoReference = photoReference; }
    public String getDisplayUrl() { return displayUrl; }
    public void setDisplayUrl(String displayUrl) { this.displayUrl = displayUrl; }
    public String getAttributionText() { return attributionText; }
    public void setAttributionText(String attributionText) { this.attributionText = attributionText; }
    public String getAttributionUrl() { return attributionUrl; }
    public void setAttributionUrl(String attributionUrl) { this.attributionUrl = attributionUrl; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

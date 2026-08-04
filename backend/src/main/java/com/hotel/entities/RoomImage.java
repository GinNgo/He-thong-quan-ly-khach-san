package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "room_images")
public class RoomImage extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private PropertyMedia media;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "alt_text_vi", columnDefinition = "nvarchar(255)")
    private String altTextVi;

    @Column(name = "alt_text_en", columnDefinition = "nvarchar(255)")
    private String altTextEn;

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

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

    public PropertyMedia getMedia() { return media; }
    public void setMedia(PropertyMedia media) { this.media = media; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getAltTextVi() { return altTextVi; }
    public void setAltTextVi(String altTextVi) { this.altTextVi = altTextVi; }
    public String getAltTextEn() { return altTextEn; }
    public void setAltTextEn(String altTextEn) { this.altTextEn = altTextEn; }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }
}

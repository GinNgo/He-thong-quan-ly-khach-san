package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "room_types", uniqueConstraints = @UniqueConstraint(name = "UK_room_types_hotel_code", columnNames = {"hotel_id", "code"}))
public class RoomType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private String code; // e.g., STANDARD, DELUXE, SUITE, VIP

    @Column(name = "name_vi", nullable = false, columnDefinition = "nvarchar(255)")
    private String nameVi;

    @Column(name = "name_en", nullable = false, columnDefinition = "nvarchar(255)")
    private String nameEn;

    @Column(name = "normalized_name", columnDefinition = "nvarchar(255)")
    private String normalizedName;

    @Column(name = "area", precision = 10, scale = 2)
    private BigDecimal area;

    @Column(name = "is_demo", nullable = false)
    private Boolean isDemo = false;

    @Column(name = "max_guest")
    private Integer maxGuest;

    @Column(name = "bed_type")
    private String bedType;

    @Column(name = "bed_count")
    private Integer bedCount;

    @Column(name = "max_adults")
    private Integer maxAdults;

    @Column(name = "max_children")
    private Integer maxChildren;

    @Column(name = "max_guests")
    private Integer maxGuests;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "hourly_price")
    private BigDecimal hourlyPrice;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "description_vi", columnDefinition = "nvarchar(max)")
    private String descriptionVi;

    @Column(name = "description_en", columnDefinition = "nvarchar(max)")
    private String descriptionEn;

    @OneToMany(mappedBy = "roomType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Room> rooms;

    @OneToMany(mappedBy = "roomType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RoomTypeImage> images;

    @PrePersist
    @PreUpdate
    void normalizeSearchName() {
        normalizedName = com.hotel.util.VietnameseTextNormalizer.normalize(nameVi);
    }

    // Getters and Setters omitted for brevity

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNameVi() {
        return nameVi;
    }

    public void setNameVi(String nameVi) {
        this.nameVi = nameVi;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public Integer getMaxGuest() {
        return maxGuest;
    }

    public void setMaxGuest(Integer maxGuest) {
        this.maxGuest = maxGuest;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public String getDescriptionVi() {
        return descriptionVi;
    }

    public void setDescriptionVi(String descriptionVi) {
        this.descriptionVi = descriptionVi;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }
}

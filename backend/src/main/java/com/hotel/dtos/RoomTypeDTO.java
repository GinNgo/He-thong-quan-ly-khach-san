package com.hotel.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class RoomTypeDTO {
    private Long id;
    @NotNull
    private Long hotelId;
    @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String code;
    @NotBlank @Size(max = 255)
    private String nameVi;
    @Size(max = 255)
    private String nameEn;
    private String normalizedName;
    @DecimalMin(value = "0.01")
    private BigDecimal area;
    private Boolean isDemo;
    private Integer maxGuest;
    private String bedType;
    @Min(1) private Integer bedCount;
    @Min(1) private Integer maxAdults;
    @Min(0) private Integer maxChildren;
    @Min(1) private Integer maxGuests;
    @DecimalMin(value = "0.00") private BigDecimal hourlyPrice;
    @Pattern(regexp = "ACTIVE|INACTIVE") private String status;
    private Long totalRooms;
    @NotNull @DecimalMin(value = "0.00")
    private BigDecimal basePrice;
    private String descriptionVi;
    private String descriptionEn;
    private Long availableRooms;
    private Long nights;
    private BigDecimal totalPrice;
    private PromotionQuoteDTO quote;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHotelId() {
        return hotelId;
    }

    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
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

    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public BigDecimal getArea() { return area; }
    public void setArea(BigDecimal area) { this.area = area; }
    public Boolean getIsDemo() { return isDemo; }
    public void setIsDemo(Boolean demo) { isDemo = demo; }

    public Integer getMaxGuest() {
        return maxGuest;
    }

    public void setMaxGuest(Integer maxGuest) {
        this.maxGuest = maxGuest;
    }

    public String getBedType() { return bedType; }
    public void setBedType(String bedType) { this.bedType = bedType; }
    public Integer getBedCount() { return bedCount; }
    public void setBedCount(Integer bedCount) { this.bedCount = bedCount; }
    public Integer getMaxAdults() { return maxAdults; }
    public void setMaxAdults(Integer maxAdults) { this.maxAdults = maxAdults; }
    public Integer getMaxChildren() { return maxChildren; }
    public void setMaxChildren(Integer maxChildren) { this.maxChildren = maxChildren; }
    public Integer getMaxGuests() { return maxGuests; }
    public void setMaxGuests(Integer maxGuests) { this.maxGuests = maxGuests; }
    public BigDecimal getHourlyPrice() { return hourlyPrice; }
    public void setHourlyPrice(BigDecimal hourlyPrice) { this.hourlyPrice = hourlyPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTotalRooms() { return totalRooms; }
    public void setTotalRooms(Long totalRooms) { this.totalRooms = totalRooms; }

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

    public Long getAvailableRooms() {
        return availableRooms;
    }

    public void setAvailableRooms(Long availableRooms) {
        this.availableRooms = availableRooms;
    }

    public Long getNights() {
        return nights;
    }

    public void setNights(Long nights) {
        this.nights = nights;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public PromotionQuoteDTO getQuote() { return quote; }
    public void setQuote(PromotionQuoteDTO quote) { this.quote = quote; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

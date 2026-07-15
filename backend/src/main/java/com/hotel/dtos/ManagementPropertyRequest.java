package com.hotel.dtos;

import lombok.Data;

@Data
public class ManagementPropertyRequest {
    private String nameVi;
    private String nameEn;
    private String propertyType;
    private Long provinceId;
    private Long wardId;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String email;
    private String website;
    private String descriptionVi;
    private String descriptionEn;
    private String checkinTime;
    private String checkoutTime;
    private Double minPrice;
    private Double maxPrice;
    private Integer starRating;
    private String mainImage;
}

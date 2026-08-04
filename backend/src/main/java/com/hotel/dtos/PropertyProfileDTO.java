package com.hotel.dtos;

import com.hotel.entities.Hotel;

public record PropertyProfileDTO(
        Long id,
        String code,
        String slug,
        String nameVi,
        String nameEn,
        String propertyType,
        String addressLine,
        String city,
        String country,
        Long provinceId,
        Long wardId,
        String descriptionVi,
        String descriptionEn,
        Integer starRating,
        String phone,
        String email,
        String website,
        String mainImage,
        String status,
        String approvalStatus,
        String operationStatus,
        String dataSource) {

    public static PropertyProfileDTO from(Hotel hotel) {
        return new PropertyProfileDTO(
                hotel.getId(), hotel.getCode(), hotel.getSlug(), hotel.getNameVi(), hotel.getNameEn(),
                hotel.getPropertyType(), hotel.getAddressLine(), hotel.getCity(), hotel.getCountry(),
                hotel.getProvinceId(), hotel.getWardId(), hotel.getDescriptionVi(), hotel.getDescriptionEn(),
                hotel.getStarRating(), hotel.getPhone(), hotel.getEmail(), hotel.getWebsite(), hotel.getMainImage(),
                hotel.getStatus(), hotel.getApprovalStatus(), hotel.getOperationStatus(), hotel.getDataSource());
    }
}

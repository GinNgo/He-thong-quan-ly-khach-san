package com.hotel.dtos;

import com.hotel.entities.Amenity;

public record AmenityDTO(
        Long id,
        String code,
        String nameVi,
        String nameEn,
        String category,
        String icon,
        Integer sortOrder,
        String status) {

    public static AmenityDTO from(Amenity amenity) {
        return new AmenityDTO(
                amenity.getId(), amenity.getCode(), amenity.getNameVi(), amenity.getNameEn(),
                amenity.getCategory(), amenity.getIcon(), amenity.getSortOrder(), amenity.getStatus());
    }
}

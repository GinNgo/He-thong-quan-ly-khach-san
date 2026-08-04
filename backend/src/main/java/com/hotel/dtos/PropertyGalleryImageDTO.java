package com.hotel.dtos;

import com.hotel.entities.PropertyImage;

public record PropertyGalleryImageDTO(
        Long id,
        Long propertyId,
        String imageUrl,
        boolean primary,
        String altTextVi,
        String altTextEn,
        int sortOrder,
        boolean managedUpload) {

    private static final String MANAGED_UPLOAD_PREFIX = "/api/public/uploads/";

    public static PropertyGalleryImageDTO from(PropertyImage image) {
        String url = image.getImageUrl();
        return new PropertyGalleryImageDTO(
                image.getId(),
                image.getHotel().getId(),
                url,
                Boolean.TRUE.equals(image.getIsPrimary()),
                image.getAltTextVi(),
                image.getAltTextEn(),
                image.getSortOrder() == null ? 0 : image.getSortOrder(),
                url != null && url.startsWith(MANAGED_UPLOAD_PREFIX));
    }
}

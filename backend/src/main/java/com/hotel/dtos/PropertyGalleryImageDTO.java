package com.hotel.dtos;

import com.hotel.entities.PropertyImage;

public record PropertyGalleryImageDTO(
        Long id,
        Long propertyId,
        Long mediaId,
        String imageUrl,
        String sourceType,
        String contentType,
        Long fileSizeBytes,
        Integer width,
        Integer height,
        boolean primary,
        String altTextVi,
        String altTextEn,
        int sortOrder,
        boolean managedUpload) {

    private static final String MANAGED_UPLOAD_PREFIX = "/api/public/uploads/";

    public static PropertyGalleryImageDTO from(PropertyImage image) {
        String url = image.getImageUrl();
        com.hotel.entities.PropertyMedia media = image.getMedia();
        return new PropertyGalleryImageDTO(
                image.getId(),
                image.getHotel().getId(),
                media == null ? null : media.getId(),
                url,
                media == null ? "LEGACY" : media.getSourceType(),
                media == null ? null : media.getContentType(),
                media == null ? null : media.getFileSizeBytes(),
                media == null ? null : media.getWidth(),
                media == null ? null : media.getHeight(),
                Boolean.TRUE.equals(image.getIsPrimary()),
                image.getAltTextVi(),
                image.getAltTextEn(),
                image.getSortOrder() == null ? 0 : image.getSortOrder(),
                media != null ? media.isManagedUpload() : url != null && url.startsWith(MANAGED_UPLOAD_PREFIX));
    }
}

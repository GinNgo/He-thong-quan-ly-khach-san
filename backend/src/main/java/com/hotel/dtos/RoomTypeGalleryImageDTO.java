package com.hotel.dtos;

import com.hotel.entities.PropertyMedia;
import com.hotel.entities.RoomTypeImage;

public record RoomTypeGalleryImageDTO(
        Long id, Long roomTypeId, Long propertyId, Long mediaId, String imageUrl,
        String sourceType, String contentType, Long fileSizeBytes, Integer width, Integer height,
        boolean primary, String altTextVi, String altTextEn, int sortOrder, boolean managedUpload) {

    public static RoomTypeGalleryImageDTO from(RoomTypeImage image) {
        PropertyMedia media = image.getMedia();
        return new RoomTypeGalleryImageDTO(
                image.getId(), image.getRoomType().getId(), image.getRoomType().getHotel().getId(),
                media == null ? null : media.getId(), image.getImageUrl(),
                media == null ? "LEGACY" : media.getSourceType(),
                media == null ? null : media.getContentType(),
                media == null ? null : media.getFileSizeBytes(),
                media == null ? null : media.getWidth(), media == null ? null : media.getHeight(),
                Boolean.TRUE.equals(image.getIsPrimary()), image.getAltTextVi(), image.getAltTextEn(),
                image.getSortOrder() == null ? 0 : image.getSortOrder(),
                media != null && media.isManagedUpload());
    }
}

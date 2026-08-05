package com.hotel.dtos;

import com.hotel.entities.RoomImage;

public record RoomGalleryImageDTO(
        Long id,
        String imageUrl,
        String altTextVi,
        String altTextEn,
        int sortOrder,
        boolean primary,
        String sourceType) {
    public static RoomGalleryImageDTO from(RoomImage image) {
        return new RoomGalleryImageDTO(
                image.getId(), image.getImageUrl(), image.getAltTextVi(), image.getAltTextEn(),
                image.getSortOrder(), Boolean.TRUE.equals(image.getIsPrimary()),
                image.getMedia() == null ? null : image.getMedia().getSourceType());
    }
}

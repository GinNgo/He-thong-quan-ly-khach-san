package com.hotel.dtos;

import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyImage;
import lombok.Data;

import java.util.Comparator;
import java.util.List;

@Data
public class PublicHotelDetailDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String addressLine;
    private String city;
    private String country;
    private Integer starRating;
    private String propertyType;
    private String approvalStatus;
    private String operationStatus;
    private String mainImage;
    private String mainImageUrl;
    private Long provinceId;
    private Long wardId;
    private List<String> galleryUrls;

    public static PublicHotelDetailDTO from(Hotel hotel) {
        PublicHotelDetailDTO dto = new PublicHotelDetailDTO();
        dto.setId(hotel.getId());
        dto.setName(firstNotBlank(hotel.getNameVi(), hotel.getName(), hotel.getNameEn()));
        dto.setSlug(hotel.getSlug());
        dto.setDescription(firstNotBlank(hotel.getDescriptionVi(), hotel.getDescription(), hotel.getDescriptionEn()));
        dto.setAddressLine(hotel.getAddressLine());
        dto.setCity(hotel.getCity());
        dto.setCountry(hotel.getCountry());
        dto.setStarRating(hotel.getStarRating());
        dto.setPropertyType(hotel.getPropertyType());
        dto.setApprovalStatus(hotel.getApprovalStatus());
        dto.setOperationStatus(hotel.getOperationStatus());
        dto.setProvinceId(hotel.getProvinceId());
        dto.setWardId(hotel.getWardId());
        List<PropertyImage> images = hotel.getImages() == null ? List.of() : hotel.getImages().stream()
                .sorted(Comparator.comparing(PropertyImage::getSortOrder).thenComparing(PropertyImage::getId))
                .toList();
        PropertyImage primary = images.stream().filter(image -> Boolean.TRUE.equals(image.getIsPrimary()))
                .findFirst().orElse(images.isEmpty() ? null : images.get(0));
        String mainImage = primary == null ? hotel.getMainImage() : primary.getImageUrl();
        dto.setMainImage(mainImage);
        dto.setMainImageUrl(mainImage);
        dto.setGalleryUrls(images.stream().map(PropertyImage::getImageUrl).filter(url -> url != null && !url.isBlank()).distinct().toList());
        return dto;
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}

package com.hotel.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationSuggestionDTO {
    private String type;
    private Long id;
    private Long parentId;
    private String name;
    private String displayName;
    private String secondaryText;
    private String address;
    private Long provinceId;
    private String provinceName;
    private Long wardId;
    private String wardName;
    private Long propertyCount;
    private String slug;
    private String propertyType;
    private String thumbnailUrl;
    private String imageUrl;
    private Double reviewScore;
    private Double distanceKm;
}

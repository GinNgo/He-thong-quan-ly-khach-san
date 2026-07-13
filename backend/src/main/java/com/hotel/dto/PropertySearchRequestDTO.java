package com.hotel.dto;

import lombok.Data;

@Data
public class PropertySearchRequestDTO {
    private String keyword;
    private Long provinceId;
    private Long wardId;
    private String checkInDate;
    private String checkOutDate;
    private Integer adultCount;
    private Integer childCount;
    private Integer roomCount;
    private Double latitude;
    private Double longitude;
    private Double radiusKm;
    private String sortBy; // NEAREST, PRICE_ASC, PRICE_DESC, POPULAR, RATING
    private int pageNumber = 1;
    private int pageSize = 20;

    // Phase 2 Filters
    private java.util.List<String> propertyTypes;
    private String stayType;
    private Double minPrice;
    private Double maxPrice;
    private java.util.List<Integer> starRatings;
    private Double minReviewScore;
    private java.util.List<Long> amenityIds;
    private Boolean freeCancellation;
    private Boolean payAtProperty;
    private Boolean breakfastIncluded;
}

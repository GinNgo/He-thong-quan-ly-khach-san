package com.hotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertySearchResponseDTO {
    private Long id;
    private String slug;
    private String name;
    private String propertyType;
    private Integer starRating;
    private String addressLine;
    private String provinceName;
    private String wardName;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    private String distanceText;
    private String thumbnailUrl;
    private List<String> galleryUrls;
    private List<String> amenities;
    private Double reviewScore;
    private Integer reviewCount;
    private Integer availableRoomCount;
    
    // Legacy field compatibility if needed
    private Double startingPrice;

    private RoomTypeSummary lowestRoomType;
    private PricingSummary pricing;
    private List<String> badges;
    
    private Boolean freeCancellation;
    private Boolean payAtProperty;
    private Boolean breakfastIncluded;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomTypeSummary {
        private Long id;
        private String name;
        private Integer maxGuests;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingSummary {
        private BigDecimal nightlyPrice;
        private BigDecimal discountedPrice;
        private Integer numberOfNights;
        private BigDecimal taxAmount;
        private BigDecimal feeAmount;
        private BigDecimal totalAmount;
        private String currency = "VND";
    }
}

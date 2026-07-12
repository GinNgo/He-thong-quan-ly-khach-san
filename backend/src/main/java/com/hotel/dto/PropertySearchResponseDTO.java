package com.hotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertySearchResponseDTO {
    private Long id;
    private String name;
    private String addressLine;
    private String mainImage;
    private Integer starRating;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    private String distanceText;
    private Double startingPrice;
}

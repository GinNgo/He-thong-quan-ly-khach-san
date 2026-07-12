package com.hotel.dto.provider;

import lombok.Data;
import java.util.List;

@Data
public class AccommodationProviderSearchRequest {
    private String keyword;
    private Double latitude;
    private Double longitude;
    private Double radiusKm;
    private List<String> propertyTypes; // HOTEL, MOTEL, HOMESTAY, RESORT...
    private Integer maxResults;
    private String language;
}

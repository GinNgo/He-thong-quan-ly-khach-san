package com.hotel.dto.provider;

import lombok.Data;
import java.util.List;

@Data
public class ProviderSearchResult {
    private String externalId;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String website;
    private Double rating;
    private Integer reviewCount;
    private String propertyType;
    private String sourceUrl;
    private String rawPayloadJson;
}

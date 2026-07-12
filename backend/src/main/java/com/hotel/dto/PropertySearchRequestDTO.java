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
}

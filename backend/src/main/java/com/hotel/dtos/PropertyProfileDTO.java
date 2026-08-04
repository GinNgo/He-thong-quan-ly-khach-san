package com.hotel.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hotel.entities.Hotel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PropertyProfileDTO {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String code;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String slug;

    @NotBlank
    @Size(max = 255)
    private String nameVi;

    @Size(max = 255)
    private String nameEn;

    @NotBlank
    @Pattern(regexp = "HOTEL|MOTEL|HOMESTAY|APARTMENT|VILLA|RESORT")
    private String propertyType;

    @NotBlank
    @Size(max = 1000)
    private String addressLine;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String city;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String country;

    @NotNull
    @Positive
    private Long provinceId;

    @NotNull
    @Positive
    private Long wardId;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @Size(max = 4000)
    private String descriptionVi;

    @Size(max = 4000)
    private String descriptionEn;

    @Min(0)
    @Max(5)
    private Integer starRating;

    @Pattern(regexp = "^\\+?[0-9][0-9 .()\\-]{7,19}$")
    private String phone;

    @Email
    @Size(max = 320)
    private String email;

    @Pattern(regexp = "(?i)^https?://[^\\s]+$")
    @Size(max = 1000)
    private String website;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$")
    private String checkinTime;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$")
    private String checkoutTime;

    @PositiveOrZero
    private Double minPrice;

    @PositiveOrZero
    private Double maxPrice;

    @Size(max = 1000)
    private String mainImage;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String operationStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean isDemo;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String dataSource;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Double averageRating;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer reviewCount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean operational;

    @AssertTrue(message = "Latitude and longitude must be provided together.")
    @JsonIgnore
    public boolean isCoordinatePairValid() {
        return (latitude == null) == (longitude == null);
    }

    @AssertTrue(message = "Minimum price must not exceed maximum price.")
    @JsonIgnore
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }

    public static PropertyProfileDTO from(Hotel hotel) {
        PropertyProfileDTO dto = new PropertyProfileDTO();
        dto.setId(hotel.getId());
        dto.setCode(hotel.getCode());
        dto.setSlug(hotel.getSlug());
        dto.setNameVi(firstNotBlank(hotel.getNameVi(), hotel.getName(), hotel.getNameEn()));
        dto.setNameEn(trimToNull(hotel.getNameEn()));
        dto.setPropertyType(hotel.getPropertyType());
        dto.setAddressLine(hotel.getAddressLine());
        dto.setCity(hotel.getCity());
        dto.setCountry(hotel.getCountry());
        dto.setProvinceId(hotel.getProvinceId());
        dto.setWardId(hotel.getWardId());
        dto.setLatitude(hotel.getLatitude());
        dto.setLongitude(hotel.getLongitude());
        dto.setDescriptionVi(firstNotBlank(hotel.getDescriptionVi(), hotel.getDescription()));
        dto.setDescriptionEn(trimToNull(hotel.getDescriptionEn()));
        dto.setStarRating(hotel.getStarRating());
        dto.setPhone(trimToNull(hotel.getPhone()));
        dto.setEmail(trimToNull(hotel.getEmail()));
        dto.setWebsite(trimToNull(hotel.getWebsite()));
        dto.setCheckinTime(trimToNull(hotel.getCheckinTime()));
        dto.setCheckoutTime(trimToNull(hotel.getCheckoutTime()));
        dto.setMinPrice(hotel.getMinPrice());
        dto.setMaxPrice(hotel.getMaxPrice());
        dto.setMainImage(trimToNull(hotel.getMainImage()));
        dto.setStatus(hotel.getStatus());
        dto.setApprovalStatus(hotel.getApprovalStatus());
        dto.setOperationStatus(hotel.getOperationStatus());
        dto.setIsDemo(hotel.getIsDemo());
        dto.setDataSource(hotel.getDataSource());
        dto.setAverageRating(hotel.getAverageRating());
        dto.setReviewCount(hotel.getReviewCount());
        dto.setOperational("ACTIVE".equalsIgnoreCase(hotel.getStatus())
                && "APPROVED".equalsIgnoreCase(hotel.getApprovalStatus())
                && "ACTIVE".equalsIgnoreCase(hotel.getOperationStatus()));
        return dto;
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

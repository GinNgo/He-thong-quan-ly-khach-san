package com.hotel.dtos;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyProfileDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidContactUrlTimeCoordinatesAndPriceRange() {
        PropertyProfileDTO profile = validProfile();
        profile.setPhone("123");
        profile.setEmail("not-an-email");
        profile.setWebsite("ftp://example.com");
        profile.setCheckinTime("25:00");
        profile.setLongitude(null);
        profile.setMinPrice(2_000_000D);
        profile.setMaxPrice(1_000_000D);

        var paths = validator.validate(profile).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .toList();

        assertTrue(paths.contains("phone"));
        assertTrue(paths.contains("email"));
        assertTrue(paths.contains("website"));
        assertTrue(paths.contains("checkinTime"));
        assertTrue(paths.contains("coordinatePairValid"));
        assertTrue(paths.contains("priceRangeValid"));
    }

    private PropertyProfileDTO validProfile() {
        PropertyProfileDTO profile = new PropertyProfileDTO();
        profile.setNameVi("LuxeStay");
        profile.setPropertyType("HOTEL");
        profile.setAddressLine("1 Test Street");
        profile.setProvinceId(1L);
        profile.setWardId(2L);
        profile.setLatitude(10.5);
        profile.setLongitude(106.7);
        profile.setPhone("+84 901 234 567");
        profile.setEmail("owner@example.com");
        profile.setWebsite("https://example.com");
        profile.setCheckinTime("14:00");
        profile.setCheckoutTime("12:00");
        profile.setMinPrice(500_000D);
        profile.setMaxPrice(1_000_000D);
        return profile;
    }
}

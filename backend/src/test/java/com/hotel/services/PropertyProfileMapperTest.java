package com.hotel.services;

import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyProfileMapperTest {

    @Mock private LocationRepository locationRepository;
    private PropertyProfileMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PropertyProfileMapper(locationRepository);
    }

    @Test
    void canonicalProfileRoundTripsEveryEditableFieldAndSynchronizesLegacyColumns() {
        arrangeLocation();
        Hotel hotel = new Hotel();
        PropertyProfileDTO profile = profile();

        mapper.apply(hotel, profile);
        PropertyProfileDTO result = PropertyProfileDTO.from(hotel);

        assertEquals("LuxeStay Riverside", hotel.getName());
        assertEquals("Mô tả chuẩn", hotel.getDescription());
        assertEquals("Đà Nẵng", hotel.getCity());
        assertEquals("Vietnam", hotel.getCountry());
        assertEquals(profile.getNameVi(), result.getNameVi());
        assertEquals(profile.getNameEn(), result.getNameEn());
        assertEquals(profile.getLatitude(), result.getLatitude());
        assertEquals(profile.getLongitude(), result.getLongitude());
        assertEquals(profile.getPhone(), result.getPhone());
        assertEquals(profile.getEmail(), result.getEmail());
        assertEquals(profile.getWebsite(), result.getWebsite());
        assertEquals(profile.getCheckinTime(), result.getCheckinTime());
        assertEquals(profile.getCheckoutTime(), result.getCheckoutTime());
        assertEquals(profile.getMinPrice(), result.getMinPrice());
        assertEquals(profile.getMaxPrice(), result.getMaxPrice());
    }

    @Test
    void rejectsMalformedWebsiteEvenWhenServiceIsCalledOutsideHttpValidation() {
        arrangeLocation();
        PropertyProfileDTO profile = profile();
        profile.setWebsite("https:///missing-host");

        assertThrows(IllegalArgumentException.class, () -> mapper.apply(new Hotel(), profile));
    }

    @Test
    void rejectsIncompleteCoordinatesAndInvertedPriceRange() {
        PropertyProfileDTO profile = profile();
        profile.setLongitude(null);
        assertThrows(IllegalArgumentException.class, () -> mapper.apply(new Hotel(), profile));

        profile.setLongitude(108.2);
        profile.setMinPrice(2_000_000D);
        profile.setMaxPrice(1_000_000D);
        assertThrows(IllegalArgumentException.class, () -> mapper.apply(new Hotel(), profile));
    }

    private void arrangeLocation() {
        Location province = new Location();
        province.setId(1L);
        province.setNameVi("Đà Nẵng");
        province.setLocationType("PROVINCE");
        Location ward = new Location();
        ward.setId(2L);
        ward.setNameVi("Hải Châu");
        ward.setLocationType("WARD");
        ward.setParent(province);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(province));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(ward));
    }

    private PropertyProfileDTO profile() {
        PropertyProfileDTO profile = new PropertyProfileDTO();
        profile.setNameVi("LuxeStay Riverside");
        profile.setNameEn("LuxeStay Riverside Hotel");
        profile.setPropertyType("HOTEL");
        profile.setAddressLine("01 Bạch Đằng");
        profile.setProvinceId(1L);
        profile.setWardId(2L);
        profile.setLatitude(16.0678);
        profile.setLongitude(108.2208);
        profile.setDescriptionVi("Mô tả chuẩn");
        profile.setDescriptionEn("Canonical description");
        profile.setStarRating(5);
        profile.setPhone("+84 901 234 567");
        profile.setEmail("stay@example.com");
        profile.setWebsite("https://example.com/stay");
        profile.setCheckinTime("14:00");
        profile.setCheckoutTime("12:00");
        profile.setMinPrice(750_000D);
        profile.setMaxPrice(2_500_000D);
        profile.setMainImage("/assets/properties/riverside.jpg");
        return profile;
    }
}

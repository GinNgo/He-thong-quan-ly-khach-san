package com.hotel.integration;

import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicDiscoveryControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LocationRepository locationRepository;
    @Autowired private HotelRepository hotelRepository;

    private Location province;
    private Location ward;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        province = location("TEST-P-" + suffix, "Tiền Giang", "PROVINCE", null);
        ward = location("TEST-W-" + suffix, "Phường Mỹ Tho", "WARD", province);

        Hotel hotel = new Hotel();
        hotel.setName("LuxeStay Riverside Mỹ Tho");
        hotel.setNameVi("Khách sạn Ánh Dương Mỹ Tho");
        hotel.setCode("TEST-H-" + suffix);
        hotel.setSlug("khach-san-anh-duong-" + suffix);
        hotel.setAddressLine("21 Đường Vườn Xanh, Phường Mỹ Tho");
        hotel.setCity("Tiền Giang");
        hotel.setCountry("Việt Nam");
        hotel.setProvinceId(province.getId());
        hotel.setWardId(ward.getId());
        hotel.setPropertyType("HOTEL");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setStatus("ACTIVE");
        hotel.setAverageRating(8.7);
        hotel.setMainImage("/assets/demo/hotel-demo-1.png");
        hotelRepository.saveAndFlush(hotel);
    }

    @Test
    void groupedSuggestions_SearchesVietnameseWithAndWithoutAccents() throws Exception {
        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "my tho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wards", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.wards[0].type").value("WARD"))
                .andExpect(jsonPath("$.wards[0].provinceName").value("Tiền Giang"));

        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "Mỹ Tho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wards", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void groupedSuggestions_SearchesPropertyNameAndAddress() throws Exception {
        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "anh duong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.properties", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.properties[0].type").value("PROPERTY"))
                .andExpect(jsonPath("$.properties[0].propertyType").value("HOTEL"))
                .andExpect(jsonPath("$.properties[0].reviewScore").value(nullValue()));

        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "21 duong vuon xanh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.properties", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void groupedSuggestions_DoesNotSearchBelowTwoCharacters() throws Exception {
        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinces", hasSize(0)))
                .andExpect(jsonPath("$.wards", hasSize(0)))
                .andExpect(jsonPath("$.properties", hasSize(0)))
                .andExpect(jsonPath("$.landmarks", hasSize(0)));
    }

    @Test
    void popularDestinations_UsesRealApprovedPropertyCount() throws Exception {
        mockMvc.perform(get("/api/public/popular-destinations").param("limit", "8")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].type").value("PROVINCE"))
                .andExpect(jsonPath("$[0].propertyCount", greaterThanOrEqualTo(1)));
    }

    private Location location(String code, String name, String type, Location parent) {
        Location location = new Location();
        location.setCode(code);
        location.setSourceCode(code);
        location.setNameVi(name);
        location.setLocationType(type);
        location.setParent(parent);
        location.setFullPath(parent == null ? name : name + ", " + parent.getNameVi());
        location.setStatus("ACTIVE");
        return locationRepository.saveAndFlush(location);
    }
}

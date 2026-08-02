package com.hotel.integration;

import com.hotel.BackendApplication;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicDiscoveryControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LocationRepository locationRepository;
    @Autowired private HotelRepository hotelRepository;

    private Location province;
    private Location currentProvince;
    private Location ward;
    private Location secondProvince;

    @BeforeEach
    void setUp() {
        String suffix = "PUB030";
        province = location("TEST-P-" + suffix, "82", "Tiền Giang", "PROVINCE", null);
        currentProvince = location("TEST-CP-" + suffix, "VN34-82", "Tỉnh Đồng Tháp", "PROVINCE", null);
        ward = location("TEST-W-" + suffix, "Phường Mỹ Tho", "WARD", province);
        secondProvince = location("TEST-P2-" + suffix, "VN34-48", "Thành phố Đà Nẵng", "PROVINCE", null);
        landmark("TEST-LM-" + suffix, "Cầu Rồng", "CULTURE", currentProvince, 10.3505, 106.3505, "ACTIVE");
        landmark("TEST-LM2-" + suffix, "Cầu Rồng", "CULTURE", secondProvince, 16.0611, 108.2277, "ACTIVE");
        landmark("TEST-LM3-" + suffix, "Điểm thử", "NATURE", province, null, null, "INACTIVE");

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
                .andExpect(jsonPath("$.wards[0].provinceName").value("Tỉnh Đồng Tháp"));

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
    void groupedSuggestions_ReturnsActiveLandmarksAndDisambiguatesProvince() throws Exception {
        mockMvc.perform(get("/api/public/search/suggestions")
                        .param("keyword", "cau rong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landmarks", hasSize(2)))
                .andExpect(jsonPath("$.landmarks[0].type").value("LANDMARK"))
                .andExpect(jsonPath("$.landmarks[0].provinceName").exists())
                .andExpect(jsonPath("$.landmarks[0].latitude").isNumber())
                .andExpect(jsonPath("$.landmarks[0].defaultRadiusKm").value(5.0));

        mockMvc.perform(get("/api/public/search/suggestions")
                        .param("keyword", "cau rong")
                        .param("provinceId", currentProvince.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landmarks", hasSize(1)))
                .andExpect(jsonPath("$.landmarks[0].provinceId").value(currentProvince.getId()));
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

    @Test
    void currentProvinceList_HidesLegacyProvinceRows() throws Exception {
        mockMvc.perform(get("/api/public/locations/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sourceCode").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.startsWith("VN34-"))))
                .andExpect(jsonPath("$[?(@.sourceCode == '82')]").isEmpty());
    }

    private Location location(String code, String name, String type, Location parent) {
        return location(code, code, name, type, parent);
    }

    private Location location(String code, String sourceCode, String name, String type, Location parent) {
        Location location = new Location();
        location.setCode(code);
        location.setSourceCode(sourceCode);
        location.setNameVi(name);
        location.setLocationType(type);
        location.setParent(parent);
        location.setFullPath(parent == null ? name : name + ", " + parent.getNameVi());
        location.setStatus("ACTIVE");
        return locationRepository.saveAndFlush(location);
    }

    private Location landmark(String code, String name, String category, Location parent,
                              Double latitude, Double longitude, String status) {
        Location location = location(code, name, "LANDMARK", parent);
        location.setCategory(category);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setDefaultRadiusKm(5d);
        location.setPopularityScore(10);
        location.setNameEn("Landmark " + code);
        location.setStatus(status);
        return locationRepository.saveAndFlush(location);
    }
}

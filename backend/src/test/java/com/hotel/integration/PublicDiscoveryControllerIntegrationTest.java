package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.entities.RoomType;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @Autowired private RoomTypeRepository roomTypeRepository;

    private Location province;
    private Location currentProvince;
    private Location ward;
    private Location secondProvince;
    private Location primaryLandmark;
    private Location secondaryLandmark;
    private Location duplicateLandmark;
    private Hotel hotel;

    @BeforeEach
    void setUp() {
        String suffix = "PUB030";
        province = location("TEST-P-" + suffix, "82", "Tiền Giang", "PROVINCE", null);
        currentProvince = location("TEST-CP-" + suffix, "VN34-82", "Tỉnh Đồng Tháp", "PROVINCE", null);
        ward = location("TEST-W-" + suffix, "Phường Mỹ Tho", "WARD", province);
        secondProvince = location("TEST-P2-" + suffix, "VN34-48", "Thành phố Đà Nẵng", "PROVINCE", null);
        location("TEST-UNMAPPED-" + suffix, "LEGACY-X", "Tỉnh thử cũ", "PROVINCE", null);
        primaryLandmark = landmark("TEST-LM-" + suffix, "Cầu Rồng", "CULTURE", currentProvince, 10.3505, 106.3505, "ACTIVE");
        primaryLandmark.setPopularityScore(20);
        location("TEST-FAKE-CURRENT-" + suffix, "VN34-FAKE", "Tinh gia lap", "PROVINCE", null);
        primaryLandmark = locationRepository.saveAndFlush(primaryLandmark);
        secondaryLandmark = landmark("TEST-LM2-" + suffix, "Cầu Rồng", "CULTURE", secondProvince, 16.0611, 108.2277, "ACTIVE");
        duplicateLandmark = landmark("TEST-LM4-" + suffix, "Cầu Rồng", "CULTURE", currentProvince, 10.3510, 106.3510, "ACTIVE");
        duplicateLandmark.setPopularityScore(5);
        duplicateLandmark = locationRepository.saveAndFlush(duplicateLandmark);
        landmark("TEST-LM3-" + suffix, "Điểm thử", "NATURE", province, null, null, "INACTIVE");
        landmark("TEST-LM5-" + suffix, "Cầu Rồng", "NATURE", currentProvince, 999d, 106.35, "ACTIVE");

        hotel = new Hotel();
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
        roomTypeRepository.saveAndFlush(roomType("ACTIVE-" + suffix, "ACTIVE"));
        roomTypeRepository.saveAndFlush(roomType("INACTIVE-" + suffix, "INACTIVE"));
    }

    @Test
    void groupedSuggestions_SearchesVietnameseWithAndWithoutAccents() throws Exception {
        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "dong thap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinces[0].id").value(currentProvince.getId()));

        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "Đồng Tháp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinces[0].id").value(currentProvince.getId()));

        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "tien giang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinces[0].id").value(currentProvince.getId()))
                .andExpect(jsonPath("$.provinces[0].name").value(currentProvince.getNameVi()));

        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "tinh thu cu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinces", hasSize(0)));

        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "tinh gia lap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provinces", hasSize(0)));

        mockMvc.perform(get("/api/public/search/suggestions").param("keyword", "my tho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wards", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.wards[0].type").value("WARD"))
                .andExpect(jsonPath("$.wards[0].provinceName").value(currentProvince.getNameVi()));

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
                .andExpect(jsonPath("$.landmarks", hasSize(3)))
                .andExpect(jsonPath("$.landmarks[0].type").value("LANDMARK"))
                .andExpect(jsonPath("$.landmarks[0].provinceName").exists())
                .andExpect(jsonPath("$.landmarks[0].latitude").isNumber())
                .andExpect(jsonPath("$.landmarks[0].defaultRadiusKm").value(5.0))
                .andExpect(jsonPath("$.landmarks[*].id", containsInAnyOrder(
                        primaryLandmark.getId().intValue(), secondaryLandmark.getId().intValue(),
                        duplicateLandmark.getId().intValue())));

        mockMvc.perform(get("/api/public/search/suggestions")
                        .param("keyword", "cau rong")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landmarks", hasSize(1)))
                .andExpect(jsonPath("$.landmarks[0].id").value(primaryLandmark.getId()));

        mockMvc.perform(get("/api/public/search/suggestions")
                        .param("keyword", "cau rong")
                        .param("provinceId", currentProvince.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landmarks", hasSize(2)))
                .andExpect(jsonPath("$.landmarks[0].provinceId").value(currentProvince.getId()));

        mockMvc.perform(get("/api/public/search/suggestions")
                        .param("keyword", "cau rong")
                        .param("provinceId", province.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landmarks", hasSize(2)))
                .andExpect(jsonPath("$.landmarks[*].provinceId", everyItem(is(currentProvince.getId().intValue()))));
    }

    @Test
    void currentAndLegacyProvinceIdsShareWardAndPropertyScopes() throws Exception {
        mockMvc.perform(get("/api/public/locations/provinces/{provinceId}/wards", currentProvince.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(ward.getId().intValue())));

        mockMvc.perform(get("/api/public/search/suggestions")
                        .param("keyword", "my tho")
                        .param("provinceId", currentProvince.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wards[*].id", hasItem(ward.getId().intValue())));

        mockMvc.perform(get("/api/public/search/suggestions")
                        .param("keyword", "anh duong")
                        .param("provinceId", currentProvince.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.properties[0].provinceId").value(currentProvince.getId()));
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
    void popularDestinations_IsDeterministicClampedFreshAndKeepsStableAssets() throws Exception {
        secondProvince.setNameVi("Alpha Destination T273");
        secondProvince = locationRepository.saveAndFlush(secondProvince);
        Location thirdProvince = location(
                "TEST-P3-T273", "VN34-68", "Beta Destination T273", "PROVINCE", null);

        // The existing hotel contributes one property to currentProvince.
        createApprovedHotels(currentProvince, 11, "POPULAR-TOP");
        createApprovedHotels(secondProvince, 10, "POPULAR-ALPHA");
        createApprovedHotels(thirdProvince, 10, "POPULAR-BETA");

        MvcResult first = mockMvc.perform(get("/api/public/popular-destinations").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, allOf(
                        containsString("max-age=60"),
                        containsString("public"),
                        containsString("must-revalidate"))))
                .andExpect(header().string("X-LuxeStay-Freshness-Seconds", "60"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains(
                        currentProvince.getId().intValue(),
                        secondProvince.getId().intValue(),
                        thirdProvince.getId().intValue())))
                .andExpect(jsonPath("$[*].propertyCount", contains(12, 10, 10)))
                .andExpect(jsonPath("$[1].imageUrl").value(expectedDestinationImage(secondProvince)))
                .andExpect(jsonPath("$[1].imageAltText", containsString(secondProvince.getNameVi())))
                .andExpect(jsonPath("$[1].imageProvenance")
                        .value("BUNDLED_DESTINATION:" + expectedDestinationAsset(secondProvince)))
                .andReturn();

        List<String> firstAlphaImages = JsonPath.read(first.getResponse().getContentAsString(),
                "$[?(@.id == " + secondProvince.getId() + ")].imageUrl");

        // Changing the count moves Beta ahead of Alpha, but must not change Alpha's image mapping.
        createApprovedHotels(thirdProvince, 1, "POPULAR-BETA-RANK-CHANGE");
        MvcResult reranked = mockMvc.perform(get("/api/public/popular-destinations").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(
                        currentProvince.getId().intValue(),
                        thirdProvince.getId().intValue(),
                        secondProvince.getId().intValue())))
                .andExpect(jsonPath("$[*].propertyCount", contains(12, 11, 10)))
                .andReturn();
        List<String> rerankedAlphaImages = JsonPath.read(reranked.getResponse().getContentAsString(),
                "$[?(@.id == " + secondProvince.getId() + ")].imageUrl");
        assertEquals(firstAlphaImages, rerankedAlphaImages);

        List<Location> additionalDestinations = List.of(
                location("TEST-P4-T273", "VN34-01", "Extra Destination 01", "PROVINCE", null),
                location("TEST-P5-T273", "VN34-04", "Extra Destination 04", "PROVINCE", null),
                location("TEST-P6-T273", "VN34-08", "Extra Destination 08", "PROVINCE", null),
                location("TEST-P7-T273", "VN34-11", "Extra Destination 11", "PROVINCE", null),
                location("TEST-P8-T273", "VN34-12", "Extra Destination 12", "PROVINCE", null),
                location("TEST-P9-T273", "VN34-14", "Extra Destination 14", "PROVINCE", null));
        for (int index = 0; index < additionalDestinations.size(); index++) {
            createApprovedHotels(additionalDestinations.get(index), 1, "POPULAR-EXTRA-" + index);
        }

        mockMvc.perform(get("/api/public/popular-destinations").param("limit", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
        mockMvc.perform(get("/api/public/popular-destinations").param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(currentProvince.getId()));
    }

    @Test
    void publicRoomCatalogFiltersInactiveTypesAndRejectsStalePropertyUrls() throws Exception {
        mockMvc.perform(get("/api/public/properties/{hotelId}/room-types", hotel.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/room-types/public/hotel/{hotelId}", hotel.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        hotel.setOperationStatus("SUSPENDED");
        hotelRepository.saveAndFlush(hotel);

        mockMvc.perform(get("/api/public/properties/{hotelId}/room-types", hotel.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/room-types/public/hotel/{hotelId}", hotel.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void currentProvinceList_HidesLegacyProvinceRows() throws Exception {
        mockMvc.perform(get("/api/public/locations/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sourceCode").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.startsWith("VN34-"))))
                .andExpect(jsonPath("$[?(@.sourceCode == '82')]").isEmpty())
                .andExpect(jsonPath("$[?(@.sourceCode == 'VN34-FAKE')]").isEmpty());
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
        location.setDescriptionVi("Địa danh kiểm thử " + name);
        location.setDescriptionEn("Test landmark " + code);
        location.setNameEn("Landmark " + code);
        location.setStatus(status);
        return locationRepository.saveAndFlush(location);
    }

    private RoomType roomType(String code, String status) {
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode(code);
        roomType.setNameVi("Loai phong " + code);
        roomType.setNameEn("Room type " + code);
        roomType.setMaxGuest(2);
        roomType.setMaxAdults(2);
        roomType.setMaxChildren(0);
        roomType.setMaxGuests(2);
        roomType.setBasePrice(new BigDecimal("500000"));
        roomType.setStatus(status);
        return roomType;
    }

    private void createApprovedHotels(Location destination, int count, String prefix) {
        for (int index = 0; index < count; index++) {
            Hotel destinationHotel = new Hotel();
            destinationHotel.setName(prefix + " Hotel " + index);
            destinationHotel.setCode("TEST-H-" + prefix + "-" + index);
            destinationHotel.setSlug((prefix + "-hotel-" + index).toLowerCase());
            destinationHotel.setAddressLine(index + " Test Street");
            destinationHotel.setCity(destination.getNameVi());
            destinationHotel.setCountry("Vietnam");
            destinationHotel.setProvinceId(destination.getId());
            destinationHotel.setPropertyType("HOTEL");
            destinationHotel.setApprovalStatus("APPROVED");
            destinationHotel.setOperationStatus("ACTIVE");
            destinationHotel.setStatus("ACTIVE");
            hotelRepository.save(destinationHotel);
        }
        hotelRepository.flush();
    }

    private String expectedDestinationImage(Location destination) {
        return "/assets/destinations/" + expectedDestinationAsset(destination);
    }

    private String expectedDestinationAsset(Location destination) {
        int assetNumber = Math.floorMod(destination.getSourceCode().hashCode(), 8) + 1;
        return "destination-" + String.format("%02d", assetNumber) + ".webp";
    }
}

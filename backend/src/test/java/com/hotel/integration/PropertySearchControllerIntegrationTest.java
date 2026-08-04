package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.entities.PropertyImage;
import com.hotel.entities.RoomType;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.RoomRepository;
import com.jayway.jsonpath.JsonPath;
import com.hotel.entities.Room;
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
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PropertySearchControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PropertyImageRepository propertyImageRepository;

    private Location primaryProvince;
    private Location legacyPrimaryProvince;
    private Location secondaryProvince;
    private Location landmark;
    private Hotel primaryHotel;

    @BeforeEach
    void setUp() {
        primaryProvince = saveLocation("TEST-P-SEARCH", "VN34-48", "Thành phố Đà Nẵng", "PROVINCE", null);
        legacyPrimaryProvince = saveLocation("TEST-P-SEARCH-LEGACY", "48", "Đà Nẵng", "PROVINCE", null);
        secondaryProvince = saveLocation("TEST-P-SEARCH-2", "VN34-68", "Da Lat", "PROVINCE", null);
        landmark = saveLocation("TEST-LM-SEARCH", "Cau Rong", "LANDMARK", primaryProvince);
        landmark.setCategory("CULTURE");
        landmark.setLatitude(16.0611);
        landmark.setLongitude(108.2277);
        landmark.setDefaultRadiusKm(5d);
        landmark.setPopularityScore(100);
        landmark = locationRepository.saveAndFlush(landmark);

        Hotel hotel = new Hotel();
        hotel.setName("Ocean View Hotel");
        hotel.setProvinceId(legacyPrimaryProvince.getId());
        hotel.setAddressLine("123 Beach Road");
        hotel.setCity("Đà Nẵng");
        hotel.setCountry("Việt Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setLatitude(16.0612);
        hotel.setLongitude(108.2278);
        primaryHotel = hotelRepository.saveAndFlush(hotel);

        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setNameEn("Standard Room");
        roomType.setNameVi("Phòng tiêu chuẩn");
        roomType.setCode("STD_ROOM");
        roomType.setBasePrice(new BigDecimal("500000"));
        roomType.setMaxGuest(2);
        roomType = roomTypeRepository.saveAndFlush(roomType);
        saveRoom(hotel, roomType, "T-101");
        saveRoom(hotel, roomType, "T-102");

        Hotel hotel2 = new Hotel();
        hotel2.setName("Mountain Retreat");
        hotel2.setProvinceId(secondaryProvince.getId());
        hotel2.setAddressLine("456 Hill Road");
        hotel2.setCity("Đà Lạt");
        hotel2.setCountry("Việt Nam");
        hotel2.setStatus("ACTIVE");
        hotel2.setApprovalStatus("APPROVED");
        hotel2.setOperationStatus("ACTIVE");
        hotel2.setLatitude(11.9404);
        hotel2.setLongitude(108.4583);
        hotel2 = hotelRepository.saveAndFlush(hotel2);

        RoomType roomType2 = new RoomType();
        roomType2.setHotel(hotel2);
        roomType2.setNameEn("Standard Room");
        roomType2.setNameVi("Phòng tiêu chuẩn");
        roomType2.setCode("STD_ROOM_2");
        roomType2.setBasePrice(new BigDecimal("450000"));
        roomType2.setMaxGuest(2);
        roomType2.setStatus("ACTIVE");
        roomType2 = roomTypeRepository.saveAndFlush(roomType2);
        saveRoom(hotel2, roomType2, "T-201");
    }

    private void saveRoom(Hotel hotel, RoomType roomType, String number) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber(number);
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setMaintenanceStatus("NONE");
        roomRepository.saveAndFlush(room);
    }

    @Test
    void searchProperties_ByCity_ShouldReturnResults() throws Exception {
        mockMvc.perform(get("/api/public/properties/search")
                        .param("provinceId", primaryProvince.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].name", is("Ocean View Hotel")))
                .andExpect(jsonPath("$.content[0].provinceName", is("Thành phố Đà Nẵng")));

        mockMvc.perform(get("/api/public/properties/search")
                        .param("provinceId", legacyPrimaryProvince.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", is("Ocean View Hotel")))
                .andExpect(jsonPath("$.content[0].provinceName", is("Thành phố Đà Nẵng")));
    }

    @Test
    void searchProperties_WithoutFilters_ShouldReturnAllApprovedProperties() throws Exception {
        mockMvc.perform(get("/api/public/properties/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void searchProperties_RequiresApprovedActiveInventoryAndAllowsDemoOutsideProduction() throws Exception {
        Hotel visible = saveEligibilityHotel("Visible T274", primaryProvince, "APPROVED", "ACTIVE", false);
        Hotel demo = saveEligibilityHotel("Demo T274", primaryProvince, "APPROVED", "ACTIVE", true);
        saveEligibilityHotel("Pending T274", primaryProvince, "PENDING", "ACTIVE", false);
        saveEligibilityHotel("Rejected T274", primaryProvince, "REJECTED", "ACTIVE", false);
        saveEligibilityHotel("Suspended T274", primaryProvince, "APPROVED", "SUSPENDED", false);
        saveEligibilityHotel("Inactive T274", primaryProvince, "APPROVED", "INACTIVE", false);

        mockMvc.perform(get("/api/public/properties/search")
                        .param("keyword", "T274")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(
                        visible.getId().intValue(), demo.getId().intValue())))
                .andExpect(jsonPath("$.content[*].name", not(hasItems(
                        "Pending T274", "Rejected T274", "Suspended T274", "Inactive T274"))));
    }

    @Test
    void searchProperties_CurrentAndLegacyProvinceIdsReturnTheSameCompatibilityScope() throws Exception {
        Hotel currentStored = saveEligibilityHotel(
                "Current Province T274", primaryProvince, "APPROVED", "ACTIVE", false);

        mockMvc.perform(get("/api/public/properties/search")
                        .param("provinceId", primaryProvince.getId().toString())
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItems(
                        primaryHotel.getId().intValue(), currentStored.getId().intValue())));

        mockMvc.perform(get("/api/public/properties/search")
                        .param("provinceId", legacyPrimaryProvince.getId().toString())
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItems(
                        primaryHotel.getId().intValue(), currentStored.getId().intValue())));
    }

    @Test
    void searchProperties_RejectsInvalidMissingAndInvertedDateRanges() throws Exception {
        String futureCheckIn = LocalDate.now().plusDays(2).toString();
        String futureCheckOut = LocalDate.now().plusDays(4).toString();

        mockMvc.perform(get("/api/public/properties/search")
                        .param("checkInDate", "04-08-2026")
                        .param("checkOutDate", futureCheckOut))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/public/properties/search")
                        .param("checkInDate", futureCheckIn))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("checkOutDate", futureCheckOut))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("checkInDate", futureCheckIn)
                        .param("checkOutDate", futureCheckIn))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngày trả phòng phải sau ngày nhận phòng."));

        mockMvc.perform(get("/api/public/properties/search")
                        .param("checkInDate", futureCheckOut)
                        .param("checkOutDate", futureCheckIn))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngày trả phòng phải sau ngày nhận phòng."));

        mockMvc.perform(get("/api/public/properties/search")
                        .param("checkInDate", "2000-01-01")
                        .param("checkOutDate", "2000-01-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("checkInDate cannot be in the past."));

        mockMvc.perform(get("/api/public/properties/search"))
                .andExpect(status().isOk());
    }

    @Test
    void searchProperties_RejectsUnknownAndNotYetSupportedFilters() throws Exception {
        mockMvc.perform(get("/api/public/properties/search")
                        .param("displayLocation", "Da Nang"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/public/properties/search")
                        .param("legacyAddressKeyword", "Bạch Đằng"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/public/properties/search")
                        .param("stayType", "DAY_USE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/properties/search")
                        .param("amenityIds", "1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/properties/search")
                        .param("freeCancellation", "true"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/properties/search")
                        .param("payAtProperty", "true"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/properties/search")
                        .param("breakfastIncluded", "true"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("stayType", "OVERNIGHT")
                        .param("freeCancellation", "false")
                        .param("payAtProperty", "false")
                        .param("breakfastIncluded", "false"))
                .andExpect(status().isOk());
    }

    @Test
    void searchProperties_WithRoomQuantity_ShouldCalculateStayPricing() throws Exception {
        String checkInDate = LocalDate.now().plusDays(10).toString();
        String checkOutDate = LocalDate.now().plusDays(12).toString();
        mockMvc.perform(get("/api/public/properties/search")
                        .param("provinceId", primaryProvince.getId().toString())
                        .param("checkInDate", checkInDate)
                        .param("checkOutDate", checkOutDate)
                        .param("adultCount", "2")
                        .param("roomCount", "2")
                        .param("minPrice", "400000")
                        .param("maxPrice", "600000")
                        .param("propertyTypes", "HOTEL")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pricing.nightlyPrice", is(500000)))
                .andExpect(jsonPath("$.content[0].pricing.numberOfNights", is(2)))
                .andExpect(jsonPath("$.content[0].pricing.roomQuantity", is(2)))
                .andExpect(jsonPath("$.content[0].pricing.subtotal", is(2000000)))
                .andExpect(jsonPath("$.content[0].pricing.taxAmount", is(300000.00)))
                .andExpect(jsonPath("$.content[0].pricing.totalAmount", is(2300000.00)));
    }

    @Test
    void searchProperties_ByLandmarkResolvesCoordinatesAndOrdersByDistance() throws Exception {
        mockMvc.perform(get("/api/public/properties/search")
                        .param("landmarkId", landmark.getId().toString())
                        .param("provinceId", legacyPrimaryProvince.getId().toString())
                        .param("latitude", "0")
                        .param("longitude", "0")
                        .param("radiusKm", "5")
                        .param("sortBy", "NEAREST")
                        .param("adultCount", "2")
                        .param("roomCount", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Ocean View Hotel")))
                .andExpect(jsonPath("$.content[0].distanceKm", notNullValue()))
                .andExpect(result -> {
                    Number distanceKm = JsonPath.read(result.getResponse().getContentAsString(),
                            "$.content[0].distanceKm");
                    assertTrue(distanceKm.doubleValue() < 1.0,
                            "landmark result should be within one kilometre");
                });
    }

    @Test
    void searchProperties_RejectsInactiveOrCoordinateLessLandmark() throws Exception {
        Location invalid = saveLocation("TEST-LM-INVALID", "Invalid Landmark", "LANDMARK", primaryProvince);
        invalid.setStatus("INACTIVE");
        invalid = locationRepository.saveAndFlush(invalid);

        mockMvc.perform(get("/api/public/properties/search")
                        .param("landmarkId", invalid.getId().toString()))
                .andExpect(status().isBadRequest());

        Location coordinateLess = saveLocation("TEST-LM-NO-COORD", "No Coordinate", "LANDMARK", primaryProvince);
        coordinateLess.setStatus("ACTIVE");
        coordinateLess = locationRepository.saveAndFlush(coordinateLess);

        mockMvc.perform(get("/api/public/properties/search")
                        .param("landmarkId", coordinateLess.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchProperties_RejectsProvinceMismatchAndInvalidRadiusOrCoordinates() throws Exception {
        mockMvc.perform(get("/api/public/properties/search")
                        .param("landmarkId", landmark.getId().toString())
                        .param("provinceId", secondaryProvince.getId().toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("landmarkId", landmark.getId().toString())
                        .param("radiusKm", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("landmarkId", landmark.getId().toString())
                        .param("radiusKm", "51"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("landmarkId", landmark.getId().toString())
                        .param("radiusKm", "NaN"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("latitude", "91")
                        .param("longitude", "108")
                        .param("radiusKm", "5"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("latitude", "NaN")
                        .param("longitude", "108")
                        .param("radiusKm", "5"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/public/properties/search")
                        .param("latitude", "16"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void featuredRatingSearch_IsDeterministicLiveAndReturnsGovernedImageFallbacks() throws Exception {
        Location featuredProvince = saveLocation(
                "TEST-P-FEATURED-T273", "VN34-04", "Featured Province T273", "PROVINCE", null);
        Hotel reviewedLeader = saveSearchableHotel(
                featuredProvince, "Reviewed Leader T273", 9.7, 200, "/catalog/leader.webp", "LEADER");
        Hotel reviewedTieA = saveSearchableHotel(
                featuredProvince, "Reviewed Tie A T273", 9.2, 50, "/catalog/tie-a.webp", "TIE-A");
        Hotel reviewedTieB = saveSearchableHotel(
                featuredProvince, "Reviewed Tie B T273", 9.2, 50, "/catalog/tie-b.webp", "TIE-B");
        Hotel unreviewed = saveSearchableHotel(
                featuredProvince, "Unreviewed T273", null, 0, "/catalog/unreviewed.webp", "UNREVIEWED");
        Hotel noImage = saveSearchableHotel(
                featuredProvince, "No Image T273", null, null, "   ", "NO-IMAGE");

        savePropertyImage(reviewedLeader, "   ", true, 0, "Ignored blank image");
        savePropertyImage(reviewedLeader, "/media/leader-gallery-first.webp", false, 1, "Gallery first");
        savePropertyImage(reviewedLeader, "/media/leader-primary.webp", true, 2, "Primary leader alt");
        savePropertyImage(reviewedTieA, "   ", true, 0, "Ignored blank image");

        MvcResult first = mockMvc.perform(get("/api/public/properties/search")
                        .param("provinceId", featuredProvince.getId().toString())
                        .param("sortBy", "RATING")
                        .param("pageSize", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-LuxeStay-Freshness", "LIVE_SEARCH"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[*].id", contains(
                        reviewedLeader.getId().intValue(),
                        reviewedTieA.getId().intValue(),
                        reviewedTieB.getId().intValue(),
                        unreviewed.getId().intValue(),
                        noImage.getId().intValue())))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("/media/leader-primary.webp"))
                .andExpect(jsonPath("$.content[0].galleryUrls", contains(
                        "/media/leader-gallery-first.webp", "/media/leader-primary.webp")))
                .andExpect(jsonPath("$.content[0].imageCount").value(2))
                .andExpect(jsonPath("$.content[0].imageAltText").value("Primary leader alt"))
                .andExpect(jsonPath("$.content[0].imageProvenance").value("PROPERTY_MEDIA"))
                .andExpect(jsonPath("$.content[1].thumbnailUrl").value("/catalog/tie-a.webp"))
                .andExpect(jsonPath("$.content[1].galleryUrls", contains("/catalog/tie-a.webp")))
                .andExpect(jsonPath("$.content[1].imageAltText").value("Reviewed Tie A T273"))
                .andExpect(jsonPath("$.content[1].imageProvenance").value("PROPERTY_CATALOG_MAIN"))
                .andExpect(jsonPath("$.content[4].thumbnailUrl").value(nullValue()))
                .andExpect(jsonPath("$.content[4].mainImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.content[4].galleryUrls", hasSize(0)))
                .andExpect(jsonPath("$.content[4].imageCount").value(0))
                .andExpect(jsonPath("$.content[4].imageAltText").value("No Image T273"))
                .andExpect(jsonPath("$.content[4].imageProvenance").value("NONE"))
                .andReturn();

        MvcResult repeated = mockMvc.perform(get("/api/public/properties/search")
                        .param("provinceId", featuredProvince.getId().toString())
                        .param("sortBy", "RATING")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> firstIds = JsonPath.read(first.getResponse().getContentAsString(), "$.content[*].id");
        List<Integer> repeatedIds = JsonPath.read(repeated.getResponse().getContentAsString(), "$.content[*].id");
        assertEquals(firstIds, repeatedIds);
    }

    private Location saveLocation(String code, String name, String type, Location parent) {
        return saveLocation(code, code, name, type, parent);
    }

    private Location saveLocation(String code, String sourceCode, String name, String type, Location parent) {
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

    private Hotel saveSearchableHotel(Location province, String name, Double rating, Integer reviewCount,
                                      String mainImage, String suffix) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setCode("TEST-FEATURED-" + suffix);
        hotel.setSlug("test-featured-" + suffix.toLowerCase());
        hotel.setProvinceId(province.getId());
        hotel.setAddressLine("1 Featured Street " + suffix);
        hotel.setCity(province.getNameVi());
        hotel.setCountry("Vietnam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setPropertyType("HOTEL");
        hotel.setAverageRating(rating);
        hotel.setReviewCount(reviewCount);
        hotel.setMainImage(mainImage);
        hotel = hotelRepository.saveAndFlush(hotel);

        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setNameEn("Featured room " + suffix);
        roomType.setNameVi("Featured room " + suffix);
        roomType.setCode("FEATURED-ROOM-" + suffix);
        roomType.setBasePrice(new BigDecimal("600000"));
        roomType.setMaxGuest(2);
        roomType.setStatus("ACTIVE");
        roomType = roomTypeRepository.saveAndFlush(roomType);
        saveRoom(hotel, roomType, "FEATURED-" + suffix);
        return hotel;
    }

    private Hotel saveEligibilityHotel(String name, Location province, String approvalStatus,
                                       String operationStatus, boolean demo) {
        String suffix = name.replaceAll("[^A-Za-z0-9]", "-").toLowerCase();
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setCode("TEST-ELIGIBILITY-" + suffix);
        hotel.setSlug("test-eligibility-" + suffix);
        hotel.setNormalizedName(name.toLowerCase());
        hotel.setProvinceId(province.getId());
        hotel.setAddressLine("1 Eligibility Street");
        hotel.setCity(province.getNameVi());
        hotel.setCountry("Vietnam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus(approvalStatus);
        hotel.setOperationStatus(operationStatus);
        hotel.setPropertyType("HOTEL");
        hotel.setIsDemo(demo);
        hotel = hotelRepository.saveAndFlush(hotel);

        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setNameEn("Eligibility room " + suffix);
        roomType.setNameVi("Eligibility room " + suffix);
        roomType.setCode("ELIGIBILITY-ROOM-" + suffix);
        roomType.setBasePrice(new BigDecimal("550000"));
        roomType.setMaxGuest(2);
        roomType.setStatus("ACTIVE");
        roomType = roomTypeRepository.saveAndFlush(roomType);
        saveRoom(hotel, roomType, "ELIGIBILITY-" + hotel.getId());
        return hotel;
    }

    private void savePropertyImage(Hotel hotel, String imageUrl, boolean primary, int sortOrder, String altText) {
        PropertyImage image = new PropertyImage();
        image.setHotel(hotel);
        image.setImageUrl(imageUrl);
        image.setIsPrimary(primary);
        image.setSortOrder(sortOrder);
        image.setAltTextVi(altText);
        propertyImageRepository.saveAndFlush(image);
    }
}

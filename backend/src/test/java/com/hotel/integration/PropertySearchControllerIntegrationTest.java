package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.entities.RoomType;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.RoomRepository;
import com.jayway.jsonpath.JsonPath;
import com.hotel.entities.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
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

    private Location primaryProvince;
    private Location legacyPrimaryProvince;
    private Location landmark;

    @BeforeEach
    void setUp() {
        primaryProvince = saveLocation("TEST-P-SEARCH", "VN34-48", "Thành phố Đà Nẵng", "PROVINCE", null);
        legacyPrimaryProvince = saveLocation("TEST-P-SEARCH-LEGACY", "48", "Đà Nẵng", "PROVINCE", null);
        Location secondaryProvince = saveLocation("TEST-P-SEARCH-2", "Da Lat", "PROVINCE", null);
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
        hotelRepository.saveAndFlush(hotel);

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
    }

    @Test
    void searchProperties_WithoutFilters_ShouldReturnAllApprovedProperties() throws Exception {
        mockMvc.perform(get("/api/public/properties/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void searchProperties_WithRoomQuantity_ShouldCalculateStayPricing() throws Exception {
        mockMvc.perform(get("/api/public/properties/search")
                        .param("provinceId", primaryProvince.getId().toString())
                        .param("checkInDate", "2026-08-01")
                        .param("checkOutDate", "2026-08-03")
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
                .andExpect(jsonPath("$.content[0].pricing.taxAmount")
                        .value(comparesEqualTo(new BigDecimal("300000")), BigDecimal.class))
                .andExpect(jsonPath("$.content[0].pricing.totalAmount")
                        .value(comparesEqualTo(new BigDecimal("2300000")), BigDecimal.class));
    }

    @Test
    void searchProperties_ByLandmarkResolvesCoordinatesAndOrdersByDistance() throws Exception {
        mockMvc.perform(get("/api/public/properties/search")
                        .param("landmarkId", landmark.getId().toString())
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
}

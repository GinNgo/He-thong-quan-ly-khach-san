package com.hotel.integration;

import com.hotel.entities.Hotel;
import com.hotel.entities.RoomType;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.RoomRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
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

    @BeforeEach
    void setUp() {
        Hotel hotel = new Hotel();
        hotel.setName("Ocean View Hotel");
        hotel.setProvinceId(1L);
        hotel.setAddressLine("123 Beach Road");
        hotel.setCity("Đà Nẵng");
        hotel.setCountry("Việt Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
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
        hotel2.setProvinceId(2L);
        hotel2.setAddressLine("456 Hill Road");
        hotel2.setCity("Đà Lạt");
        hotel2.setCountry("Việt Nam");
        hotel2.setStatus("ACTIVE");
        hotel2.setApprovalStatus("APPROVED");
        hotel2.setOperationStatus("ACTIVE");
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
                        .param("provinceId", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].name", is("Ocean View Hotel")));
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
                        .param("provinceId", "1")
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
                .andExpect(jsonPath("$.content[0].pricing.taxAmount", is(300000.00)))
                .andExpect(jsonPath("$.content[0].pricing.totalAmount", is(2300000.00)));
    }
}

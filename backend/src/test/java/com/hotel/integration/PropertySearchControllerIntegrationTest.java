package com.hotel.integration;

import com.hotel.entities.Hotel;
import com.hotel.entities.RoomType;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoomTypeRepository;
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

    @BeforeEach
    void setUp() {
        Hotel hotel = new Hotel();
        hotel.setName("Ocean View Hotel");
        hotel.setProvinceId(1L);
        hotel.setAddressLine("123 Beach Road");
        hotel.setStatus("ACTIVE");
        hotelRepository.saveAndFlush(hotel);

        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setNameEn("Standard Room");
        roomType.setNameVi("Phòng tiêu chuẩn");
        roomType.setCode("STD_ROOM");
        roomType.setBasePrice(new BigDecimal("500000"));
        roomType.setMaxGuest(2);
        roomTypeRepository.saveAndFlush(roomType);

        Hotel hotel2 = new Hotel();
        hotel2.setName("Mountain Retreat");
        hotel2.setProvinceId(2L);
        hotel2.setAddressLine("456 Hill Road");
        hotel2.setStatus("ACTIVE");
        hotelRepository.saveAndFlush(hotel2);
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
}

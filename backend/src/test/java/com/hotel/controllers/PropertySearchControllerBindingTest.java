package com.hotel.controllers;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.services.PropertySearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropertySearchControllerBindingTest {

    @Test
    void canonicalSearchBindsEveryCurrentlySupportedRequestField() throws Exception {
        PropertySearchService searchService = mock(PropertySearchService.class);
        when(searchService.searchProperties(any(PropertySearchRequestDTO.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PropertySearchController(searchService))
                .build();

        mockMvc.perform(get("/api/public/properties/search")
                        .param("keyword", "beach")
                        .param("provinceId", "10")
                        .param("wardId", "11")
                        .param("landmarkId", "12")
                        .param("checkInDate", "2030-08-10")
                        .param("checkOutDate", "2030-08-12")
                        .param("adultCount", "2")
                        .param("childCount", "1")
                        .param("roomCount", "2")
                        .param("latitude", "16.0611")
                        .param("longitude", "108.2277")
                        .param("radiusKm", "5.5")
                        .param("sortBy", "NEAREST")
                        .param("pageNumber", "2")
                        .param("pageSize", "25")
                        .param("propertyTypes", "HOTEL,RESORT")
                        .param("stayType", "OVERNIGHT")
                        .param("minPrice", "400000")
                        .param("maxPrice", "900000")
                        .param("starRatings", "4,5")
                        .param("minReviewScore", "8.5")
                        .param("freeCancellation", "false")
                        .param("payAtProperty", "false")
                        .param("breakfastIncluded", "false"))
                .andExpect(status().isOk());

        ArgumentCaptor<PropertySearchRequestDTO> captor = ArgumentCaptor.forClass(PropertySearchRequestDTO.class);
        verify(searchService).searchProperties(captor.capture());
        PropertySearchRequestDTO request = captor.getValue();
        assertAll(
                () -> assertEquals("beach", request.getKeyword()),
                () -> assertEquals(10L, request.getProvinceId()),
                () -> assertEquals(11L, request.getWardId()),
                () -> assertEquals(12L, request.getLandmarkId()),
                () -> assertEquals("2030-08-10", request.getCheckInDate()),
                () -> assertEquals("2030-08-12", request.getCheckOutDate()),
                () -> assertEquals(2, request.getAdultCount()),
                () -> assertEquals(1, request.getChildCount()),
                () -> assertEquals(2, request.getRoomCount()),
                () -> assertEquals(Double.valueOf(16.0611), request.getLatitude()),
                () -> assertEquals(Double.valueOf(108.2277), request.getLongitude()),
                () -> assertEquals(Double.valueOf(5.5), request.getRadiusKm()),
                () -> assertEquals("NEAREST", request.getSortBy()),
                () -> assertEquals(2, request.getPageNumber()),
                () -> assertEquals(25, request.getPageSize()),
                () -> assertEquals(List.of("HOTEL", "RESORT"), request.getPropertyTypes()),
                () -> assertEquals("OVERNIGHT", request.getStayType()),
                () -> assertEquals(Double.valueOf(400000), request.getMinPrice()),
                () -> assertEquals(Double.valueOf(900000), request.getMaxPrice()),
                () -> assertEquals(List.of(4, 5), request.getStarRatings()),
                () -> assertEquals(Double.valueOf(8.5), request.getMinReviewScore()),
                () -> assertEquals(false, request.getFreeCancellation()),
                () -> assertEquals(false, request.getPayAtProperty()),
                () -> assertEquals(false, request.getBreakfastIncluded()));
    }
}

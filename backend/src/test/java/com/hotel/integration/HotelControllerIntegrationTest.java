package com.hotel.integration;

import com.hotel.controllers.HotelController;
import com.hotel.BackendApplication;
import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.entities.User;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.HotelManagementService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PropertyRegistrationService;
import com.hotel.services.PropertySearchService;
import com.hotel.observability.OperationalMetrics;
import com.hotel.services.RoomTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.hotel.config.SecurityConfig;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(HotelController.class)
@ContextConfiguration(classes = BackendApplication.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class HotelControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HotelManagementService hotelService;

    @MockBean
    private RoomTypeService roomTypeService;

    @MockBean
    private PropertySearchService propertySearchService;

    @MockBean
    private PropertyAccessService propertyAccessService;

    @MockBean
    private PropertyRegistrationService propertyRegistrationService;

    @MockBean
    private OperationalMetrics operationalMetrics;

    @MockBean
    private TenantFilterInterceptor tenantFilterInterceptor;

    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void allowRequestsThroughTenantInterceptor() throws Exception {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void getMyHotels_WithAuth_ShouldReturn200() throws Exception {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("partner");
        mockUser.setPasswordHash("hash");
        mockUser.setRoles(new HashSet<>());
        
        Map<String, Integer> featureLimits = new HashMap<>();
        featureLimits.put("HOTEL", 1);

        CustomUserDetails userDetails = new CustomUserDetails(
                mockUser.getUsername(),
                mockUser.getPasswordHash(),
                new HashSet<>(),
                new HashMap<>(),
                mockUser.getId(),
                null,
                featureLimits
        );

        mockMvc.perform(get("/api/v1/hotels/my-hotels")
                        .with(user(userDetails)))
                .andExpect(status().isOk());
    }

    @Test
    void getMyHotels_WithoutAuth_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/hotels/my-hotels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyPublicSearch_DelegatesLegacyParametersToCanonicalSearch() throws Exception {
        when(propertySearchService.searchProperties(any(PropertySearchRequestDTO.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/hotels/public/search")
                        .param("city", "Bạch Đằng")
                        .param("provinceId", "48")
                        .param("wardId", "20194")
                        .param("checkIn", "2030-08-10")
                        .param("checkOut", "2030-08-12")
                        .param("guests", "3")
                        .param("pageNumber", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(PropertySearchRequestDTO.class);
        verify(propertySearchService).searchProperties(requestCaptor.capture());
        PropertySearchRequestDTO request = requestCaptor.getValue();
        assertThat(request.getKeyword()).isNull();
        assertThat(request.getLegacyAddressKeyword()).isEqualTo("Bạch Đằng");
        assertThat(request.getProvinceId()).isEqualTo(48L);
        assertThat(request.getWardId()).isEqualTo(20194L);
        assertThat(request.getCheckInDate()).isEqualTo("2030-08-10");
        assertThat(request.getCheckOutDate()).isEqualTo("2030-08-12");
        assertThat(request.getAdultCount()).isEqualTo(3);
        assertThat(request.getChildCount()).isNull();
        assertThat(request.getRoomCount()).isNull();
        assertThat(request.getPageNumber()).isEqualTo(2);
        assertThat(request.getPageSize()).isEqualTo(5);
    }

    @Test
    void legacyPublicSearch_ReturnsCanonicalDtoPageWithoutHotelEntityFields() throws Exception {
        PropertySearchResponseDTO response = new PropertySearchResponseDTO();
        response.setId(91L);
        response.setName("Canonical property");
        response.setStartingPrice(750_000D);
        when(propertySearchService.searchProperties(any(PropertySearchRequestDTO.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/hotels/public/search"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-LuxeStay-Freshness", "LIVE_SEARCH"))
                .andExpect(jsonPath("$.content[0].id").value(91))
                .andExpect(jsonPath("$.content[0].name").value("Canonical property"))
                .andExpect(jsonPath("$.content[0].startingPrice").value(750000))
                .andExpect(jsonPath("$.content[0].approvalStatus").doesNotExist())
                .andExpect(jsonPath("$.content[0].operationStatus").doesNotExist())
                .andExpect(jsonPath("$.content[0].owner").doesNotExist());

        verifyNoInteractions(hotelService, roomTypeService);
    }

    @Test
    void legacyPublicSearch_LeavesEligibilityToCanonicalService() throws Exception {
        when(propertySearchService.searchProperties(any(PropertySearchRequestDTO.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/hotels/public/search"))
                .andExpect(status().isOk());

        verify(propertySearchService).searchProperties(any(PropertySearchRequestDTO.class));
        verify(hotelService, never()).searchHotels(any(), any());
        verifyNoInteractions(roomTypeService);
    }

    @Test
    void legacyPublicSearch_RejectsObsoleteDistrictAndInvalidGuestCount() throws Exception {
        mockMvc.perform(get("/api/v1/hotels/public/search").param("districtId", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("districtId is no longer supported; use provinceId and wardId."));

        mockMvc.perform(get("/api/v1/hotels/public/search").param("guests", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("guests must be greater than zero."));

        verifyNoInteractions(propertySearchService, hotelService, roomTypeService);
    }

}

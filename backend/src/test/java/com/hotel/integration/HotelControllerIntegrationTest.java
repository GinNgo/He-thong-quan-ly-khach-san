package com.hotel.integration;

import com.hotel.controllers.HotelController;
import com.hotel.BackendApplication;
import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.entities.User;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.HotelManagementService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PropertyApprovalWorkflowService;
import com.hotel.services.PropertyRegistrationService;
<<<<<<< HEAD
import com.hotel.services.PropertySearchService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.exceptions.ResourceNotFoundException;
=======
import com.hotel.services.PublicPlacementDisclosureService;
>>>>>>> codex/ui-functional-audit-polish
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
<<<<<<< HEAD
    private PropertyApprovalWorkflowService propertyApprovalWorkflowService;

    @MockBean
    private PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;
=======
    private PublicPlacementDisclosureService publicPlacementDisclosureService;
>>>>>>> codex/ui-functional-audit-polish

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
    void publicDetail_UsesCanonicalEligibilityPolicyAndDisablesCaching() throws Exception {
        com.hotel.entities.Hotel hotel = new com.hotel.entities.Hotel();
        hotel.setId(44L);
        hotel.setNameVi("Canonical public detail");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        when(publicInventoryEligibilityPolicy.requirePublicProperty(44L)).thenReturn(hotel);

        mockMvc.perform(get("/api/v1/hotels/public/{id}", 44L))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.id").value(44))
                .andExpect(jsonPath("$.name").value("Canonical public detail"));

        verify(publicInventoryEligibilityPolicy).requirePublicProperty(44L);
        verify(hotelService, never()).getHotelById(44L);
        verifyNoMoreInteractions(publicInventoryEligibilityPolicy);
    }

    @Test
    void publicDetail_ReturnsIndistinguishable404WhenCanonicalPolicyRejectsProperty() throws Exception {
        when(publicInventoryEligibilityPolicy.requirePublicProperty(91L))
                .thenThrow(new ResourceNotFoundException("The requested property is not publicly available."));

        mockMvc.perform(get("/api/v1/hotels/public/{id}", 91L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(publicInventoryEligibilityPolicy).requirePublicProperty(91L);
        verify(hotelService, never()).getHotelById(91L);
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

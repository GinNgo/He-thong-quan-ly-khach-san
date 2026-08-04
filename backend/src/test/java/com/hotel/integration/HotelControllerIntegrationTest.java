package com.hotel.integration;

import com.hotel.controllers.HotelController;
import com.hotel.BackendApplication;
import com.hotel.entities.User;
import com.hotel.entities.Hotel;
import com.hotel.dtos.PropertyApprovalDecisionResponse;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.HotelManagementService;
import com.hotel.services.PropertyApprovalWorkflowService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.observability.OperationalMetrics;
import com.hotel.services.RoomTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.time.LocalDateTime;

import com.hotel.config.SecurityConfig;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private PropertyApprovalWorkflowService propertyApprovalWorkflowService;

    @MockBean
    private PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;

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
    void publicDetailDelegatesToCanonicalEligibilityPolicy() throws Exception {
        Hotel hotel = new Hotel();
        hotel.setId(51L);
        hotel.setName("Harbor Hotel");
        hotel.setNameVi("Harbor Hotel");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        when(publicInventoryEligibilityPolicy.requirePublicProperty(51L)).thenReturn(hotel);

        mockMvc.perform(get("/api/v1/hotels/public/51"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(51));

        verify(publicInventoryEligibilityPolicy).requirePublicProperty(51L);
    }

    @Test
    void pendingPublicDetailIsHiddenAsNotFound() throws Exception {
        when(publicInventoryEligibilityPolicy.requirePublicProperty(51L))
                .thenThrow(new ResourceNotFoundException("The requested property is not publicly available."));

        mockMvc.perform(get("/api/v1/hotels/public/51"))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyApprovalDelegatesWithAuthoritativeReviewerId() throws Exception {
        CustomUserDetails admin = principal(99L, "SUPER_ADMIN");
        when(propertyApprovalWorkflowService.approve(99L, 51L))
                .thenReturn(new PropertyApprovalDecisionResponse(
                        51L, "ACTIVE", "APPROVED", "ACTIVE", "ACTIVE",
                        99L, LocalDateTime.of(2026, 8, 4, 6, 30), null));

        mockMvc.perform(post("/api/v1/hotels/51/approve")
                        .with(user(admin))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedByUserId").value(99));

        verify(propertyApprovalWorkflowService).approve(99L, 51L);
    }

    @Test
    void legacyRejectionDelegatesValidatedReasonToSameWorkflow() throws Exception {
        CustomUserDetails admin = principal(99L, "SUPER_ADMIN");
        when(propertyApprovalWorkflowService.reject(99L, 51L, "Address evidence is incomplete."))
                .thenReturn(new PropertyApprovalDecisionResponse(
                        51L, "REJECTED", "REJECTED", "INACTIVE", "INACTIVE",
                        99L, LocalDateTime.of(2026, 8, 4, 6, 30), "Address evidence is incomplete."));

        mockMvc.perform(post("/api/v1/hotels/51/reject")
                        .with(user(admin))
                        .contentType("application/json")
                        .content("{\"reason\":\"  Address evidence is incomplete.  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Address evidence is incomplete."));

        verify(propertyApprovalWorkflowService)
                .reject(99L, 51L, "Address evidence is incomplete.");
    }

    private CustomUserDetails principal(Long userId, String authority) {
        return new CustomUserDetails(
                "admin@example.test",
                "hash",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(authority)),
                java.util.Map.of(),
                userId,
                null,
                java.util.Map.of());
    }

}

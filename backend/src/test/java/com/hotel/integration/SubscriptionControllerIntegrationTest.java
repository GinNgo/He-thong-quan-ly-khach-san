package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.config.SecurityConfig;
import com.hotel.controllers.SubscriptionController;
import com.hotel.dtos.AccountSubscriptionDTO;
import com.hotel.dtos.SubscriptionFeatureDTO;
import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
import com.hotel.entities.User;
import com.hotel.observability.OperationalMetrics;
<<<<<<< HEAD
import com.hotel.security.*;
=======
import com.hotel.security.CustomUserDetails;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;
>>>>>>> codex/ui-functional-audit-polish
import com.hotel.services.SubscriptionCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@ContextConfiguration(classes = BackendApplication.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SubscriptionControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockBean SubscriptionCatalogService catalogService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean TenantFilterInterceptor tenantFilterInterceptor;
    @MockBean OperationalMetrics operationalMetrics;

<<<<<<< HEAD
    @BeforeEach void allowTenantFilter() {
=======
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionPlanRepository planRepository;

    @MockBean
    private AccountSubscriptionRepository accountSubscriptionRepository;

    @MockBean
    private SubscriptionFeatureService featureService;

    @MockBean
    private SubscriptionCatalogService catalogService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private TenantFilterInterceptor tenantFilterInterceptor;

    @MockBean
    private OperationalMetrics operationalMetrics;

    @BeforeEach
    void allowMvcRequestsThroughTenantFilter() {
>>>>>>> codex/ui-functional-audit-polish
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test void plansArePublicPrivacyDtos() throws Exception {
        when(catalogService.getActivePlans()).thenReturn(List.of(new SubscriptionPlanDTO(5L, "PREMIUM",
                "Cao cap", "Premium", "YEARLY", new BigDecimal("12000000"), "VND", false, "ACTIVE",
                List.of(new SubscriptionFeatureDTO("MAX_ROOMS", "Phong", "Rooms", "NUMERIC", 50)))));
        mockMvc.perform(get("/api/subscriptions/plans"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("PREMIUM"))
                .andExpect(jsonPath("$[0].features[0].limit").value(50))
                .andExpect(jsonPath("$[0].features[0].plan").doesNotExist());
    }

    @Test void currentRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/subscriptions/me").param("targetHotelId", "9"))
                .andExpect(status().isUnauthorized());
    }

    @Test void currentRequiresExplicitHotelScope() throws Exception {
        mockMvc.perform(get("/api/subscriptions/me").with(user(details(1L))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(catalogService);
    }

    @Test void currentDelegatesSelectedHotelAndDoesNotExposeEntityGraph() throws Exception {
        when(catalogService.getCurrent(9L)).thenReturn(new AccountSubscriptionDTO(9L, "PLATFORM", true,
                5L, "PREMIUM", "Premium", "ACTIVE", null, null, false, "contract-1", null));
        mockMvc.perform(get("/api/subscriptions/me").param("targetHotelId", "9").with(user(details(1L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetHotelId").value(9))
                .andExpect(jsonPath("$.platformAuthoritative").value(true))
                .andExpect(jsonPath("$.user").doesNotExist()).andExpect(jsonPath("$.plan.features").doesNotExist());
        verify(catalogService).getCurrent(9L);
    }

    @Test void featuresDelegateSelectedHotel() throws Exception {
        when(catalogService.getFeatures(9L)).thenReturn(Map.of("MAX_ROOMS", 50));
        mockMvc.perform(get("/api/subscriptions/me/features").param("targetHotelId", "9")
                        .with(user(details(1L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.MAX_ROOMS").value(50));
        verify(catalogService).getFeatures(9L);
    }

    @Test void usageDelegatesSelectedHotel() throws Exception {
        when(catalogService.getUsage(9L)).thenReturn(new SubscriptionUsageDTO(9L, "PLATFORM", true,
                "PREMIUM", "ACTIVE", null, null, false, Map.of("MAX_ROOMS", 50),
                Map.of("MAX_ROOMS", 4L), List.of(), null));
        mockMvc.perform(get("/api/subscriptions/me/usage").param("targetHotelId", "9")
                        .with(user(details(1L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usage.MAX_ROOMS").value(4))
                .andExpect(jsonPath("$.targetHotelId").value(9));
        verify(catalogService).getUsage(9L);
    }

    private CustomUserDetails details(Long id) {
        User user = new User(); user.setId(id); user.setUsername("testuser"); user.setPasswordHash("hash");
        user.setRoles(new HashSet<>());
        return new CustomUserDetails(user.getUsername(), user.getPasswordHash(), new HashSet<>(),
                new HashMap<>(), user.getId(), null, new HashMap<>());
    }
}

package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.config.SecurityConfig;
import com.hotel.controllers.SubscriptionController;
import com.hotel.dtos.AccountSubscriptionDTO;
import com.hotel.dtos.SubscriptionEntitlementDTO;
import com.hotel.dtos.SubscriptionFeatureDTO;
import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
import com.hotel.entities.User;
import com.hotel.security.CustomUserDetails;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;
import com.hotel.services.SubscriptionCatalogService;
import com.hotel.services.SubscriptionFeatureService;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@ContextConfiguration(classes = BackendApplication.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SubscriptionControllerIntegrationTest {

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

    @BeforeEach
    void allowMvcRequestsThroughTenantFilter() {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void getAllPlans_ShouldReturn200() throws Exception {
        SubscriptionPlanDTO plan = new SubscriptionPlanDTO(
                5L,
                "PREMIUM",
                "Cao cap",
                "Premium",
                "YEARLY",
                new BigDecimal("12000000"),
                "VND",
                false,
                "ACTIVE",
                List.of(new SubscriptionFeatureDTO(
                        "MAX_PROPERTIES", "So co so", "Properties", "NUMERIC", 5)));
        when(catalogService.getActivePlans()).thenReturn(List.of(plan));

        mockMvc.perform(get("/api/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").value("PREMIUM"))
                .andExpect(jsonPath("$[0].price").value(12000000))
                .andExpect(jsonPath("$[0].currency").value("VND"))
                .andExpect(jsonPath("$[0].features[0].code").value("MAX_PROPERTIES"))
                .andExpect(jsonPath("$[0].features[0].limit").value(5));
    }

    @Test
    void getMySubscriptions_WithoutAuth_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/subscriptions/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMySubscriptions_WithAuth_ShouldReturn200() throws Exception {
        CustomUserDetails userDetails = userDetails(1L);
        SubscriptionPlanDTO plan = new SubscriptionPlanDTO(
                5L, "PREMIUM", "Cao cap", "Premium", "YEARLY",
                new BigDecimal("12000000"), "VND", false, "ACTIVE", List.of());
        when(catalogService.getSubscriptions(1L)).thenReturn(List.of(new AccountSubscriptionDTO(
                31L,
                plan,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2027, 8, 1, 0, 0),
                false,
                "ACTIVE")));

        mockMvc.perform(get("/api/subscriptions/me")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(31L))
                .andExpect(jsonPath("$[0].plan.code").value("PREMIUM"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(catalogService).getSubscriptions(1L);
    }

    @Test
    void getMyFeatures_WithAuth_ShouldReturn200() throws Exception {
        CustomUserDetails userDetails = userDetails(1L);
        when(featureService.getActiveFeaturesForUser(1L))
                .thenReturn(Map.of("MAX_PROPERTIES", 5, "MAX_ROOMS", -1));

        mockMvc.perform(get("/api/subscriptions/me/features")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.MAX_PROPERTIES").value(5))
                .andExpect(jsonPath("$.MAX_ROOMS").value(-1));

        verify(featureService).getActiveFeaturesForUser(1L);
    }

    @Test
    void getMyUsage_WithoutAuth_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/subscriptions/me/usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyUsage_WithAuth_ShouldSerializeLifecycleAndEntitlements() throws Exception {
        when(catalogService.getUsage(1L)).thenReturn(new SubscriptionUsageDTO(
                "PREMIUM",
                "ACTIVE",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2027, 8, 1, 0, 0),
                false,
                Map.of("MAX_PROPERTIES", 5),
                Map.of("MAX_PROPERTIES", 2L),
                List.of(new SubscriptionEntitlementDTO(
                        "MAX_PROPERTIES", "So co so", "Properties", 5, 2L, true))));

        mockMvc.perform(get("/api/subscriptions/me/usage")
                        .with(user(userDetails(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PREMIUM"))
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.limits.MAX_PROPERTIES").value(5))
                .andExpect(jsonPath("$.usage.MAX_PROPERTIES").value(2))
                .andExpect(jsonPath("$.features[0].allowed").value(true));

        verify(catalogService).getUsage(1L);
    }

    private CustomUserDetails userDetails(Long userId) {
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("testuser");
        mockUser.setPasswordHash("hash");
        mockUser.setRoles(new HashSet<>());
        return new CustomUserDetails(
                mockUser.getUsername(),
                mockUser.getPasswordHash(),
                new HashSet<>(),
                new HashMap<>(),
                mockUser.getId(),
                null,
                new HashMap<>());
    }
}

package com.hotel.integration;

import com.hotel.entities.User;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotel.controllers.SubscriptionController;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.services.SubscriptionFeatureService;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.hotel.config.SecurityConfig;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtTokenProvider;

@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class})
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
    private UserDetailsService userDetailsService;

    @Test
    void getAllPlans_ShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getMySubscriptions_WithoutAuth_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/subscriptions/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMySubscriptions_WithAuth_ShouldReturn200() throws Exception {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setPasswordHash("hash");
        mockUser.setRoles(new HashSet<>());
        CustomUserDetails userDetails = new CustomUserDetails(
                mockUser.getUsername(),
                mockUser.getPasswordHash(),
                new HashSet<>(),
                new java.util.HashMap<>(),
                mockUser.getId(),
                null,
                new java.util.HashMap<>()
        );

        mockMvc.perform(get("/api/subscriptions/me")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getMyFeatures_WithAuth_ShouldReturn200() throws Exception {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setPasswordHash("hash");
        mockUser.setRoles(new HashSet<>());
        CustomUserDetails userDetails = new CustomUserDetails(
                mockUser.getUsername(),
                mockUser.getPasswordHash(),
                new HashSet<>(),
                new java.util.HashMap<>(),
                mockUser.getId(),
                null,
                new java.util.HashMap<>()
        );

        mockMvc.perform(get("/api/subscriptions/me/features")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }
}

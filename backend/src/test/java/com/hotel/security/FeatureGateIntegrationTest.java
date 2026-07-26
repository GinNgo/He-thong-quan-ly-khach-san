package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureGateIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // 1. No token -> 401
    @Test
    void whenNoToken_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/hotels/my-hotels"))
                .andExpect(status().isUnauthorized());
    }

    // 2. Token lacks HOTEL feature -> 403 FORBIDDEN_FEATURE
    @Test
    void whenTokenLacksFeature_thenReturns403() throws Exception {
        CustomUserDetails user = new CustomUserDetails(
                "user1", "pass",
                List.of(new SimpleGrantedAuthority("PROPERTY_OWNER")),
                Collections.emptyMap(), 1L, null, Collections.emptyMap() // No feature limits
        );

        mockMvc.perform(get("/api/v1/hotels/my-hotels").with(user(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_FEATURE"));
    }

    // 3. Token has HOTEL feature (limit > 0) -> 200
    @Test
    void whenTokenHasFeature_thenReturns200() throws Exception {
        CustomUserDetails user = new CustomUserDetails(
                "user2", "pass",
                List.of(new SimpleGrantedAuthority("PROPERTY_OWNER")),
                Collections.emptyMap(), 1L, null, Map.of("HOTEL", 1) // limit = 1
        );

        mockMvc.perform(get("/api/v1/hotels/my-hotels").with(user(user)))
                .andExpect(status().isOk());
    }
}
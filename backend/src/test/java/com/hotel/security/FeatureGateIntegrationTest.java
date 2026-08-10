package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
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

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FeatureGateIntegrationTest.FeatureGateProbeController.class)
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

    // Read-only property discovery remains available even when the plan has no growth quota.
    @Test
    void whenReadOnlyPropertyListHasNoFeature_thenReturns200() throws Exception {
        CustomUserDetails user = new CustomUserDetails(
                "user1", "pass",
                List.of(new SimpleGrantedAuthority("PROPERTY_OWNER")),
                Collections.emptyMap(), 1L, null, Collections.emptyMap()
        );

        mockMvc.perform(get("/api/v1/hotels/my-hotels").with(user(user)))
                .andExpect(status().isOk());
    }

    @Test
    void whenMutationProbeLacksFeature_thenReturns403() throws Exception {
        mockMvc.perform(get("/api/test/feature-gate").with(user(propertyOwner(Collections.emptyMap()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_FEATURE"));
    }

    @Test
    void whenMutationProbeHasFeature_thenReturns200() throws Exception {
        mockMvc.perform(get("/api/test/feature-gate")
                        .with(user(propertyOwner(Map.of("MAX_PROPERTIES", 1)))))
                .andExpect(status().isOk());
    }

    @Test
    void whenMutationProbeHasFeatureButWrongRole_thenReturns403() throws Exception {
        CustomUserDetails user = new CustomUserDetails(
                "user2", "pass",
                List.of(new SimpleGrantedAuthority("CUSTOMER")),
                Collections.emptyMap(), 1L, null, Map.of("MAX_PROPERTIES", 1)
        );

        mockMvc.perform(get("/api/test/feature-gate").with(user(user)))
                .andExpect(status().isForbidden());
    }

    private CustomUserDetails propertyOwner(Map<String, Integer> featureLimits) {
        return new CustomUserDetails(
                "owner", "pass",
                List.of(new SimpleGrantedAuthority("PROPERTY_OWNER")),
                Collections.emptyMap(), 1L, null, featureLimits);
    }

    @RestController
    @RequestMapping("/api/test")
    static class FeatureGateProbeController {

        @GetMapping("/feature-gate")
        @PreAuthorize("hasAuthority('PROPERTY_OWNER')")
        @RequireFeature("MAX_PROPERTIES")
        ResponseEntity<Map<String, String>> featureGate() {
            return ResponseEntity.ok(Map.of("status", "allowed"));
        }
    }
}

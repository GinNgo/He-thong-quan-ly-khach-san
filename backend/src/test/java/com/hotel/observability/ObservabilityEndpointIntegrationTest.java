package com.hotel.observability;

import com.hotel.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesPublicHealthProbesWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectsOperationalMetricsFromAnonymousCallers() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void limitsOperationalMetricsToPlatformOperators() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .with(SecurityMockMvcRequestPostProcessors.user("customer")
                                .authorities(() -> "CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics")
                        .with(SecurityMockMvcRequestPostProcessors.user("operator")
                                .authorities(() -> "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }
}

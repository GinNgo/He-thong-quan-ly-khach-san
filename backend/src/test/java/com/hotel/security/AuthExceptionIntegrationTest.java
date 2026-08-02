package com.hotel.security;

import com.hotel.BackendApplication;
import com.hotel.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;
import java.security.Key;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthExceptionIntegrationTest.DummyPermissionController.class)
public class AuthExceptionIntegrationTest {

    @RestController
    static class DummyPermissionController {
        @GetMapping("/api/test/permission")
        @Permission(function = FunctionCode.SYSTEM, action = ActionCode.VIEW)
        public String testPermission() {
            return "OK";
        }

        @GetMapping("/api/test/feature")
        @RequireFeature("ADVANCED_REPORTS")
        public String testFeature() {
            return "OK";
        }

        @GetMapping("/api/test/invalid-request")
        public String invalidRequest() {
            throw new IllegalArgumentException("Invalid request fixture");
        }

        @GetMapping("/api/test/conflict")
        public String conflict() {
            throw new IllegalStateException("Conflict fixture");
        }

        @GetMapping("/api/test/not-found")
        public String notFound() {
            throw new ResourceNotFoundException("Missing fixture");
        }

        @GetMapping("/api/test/internal-error")
        public String internalError() {
            throw new RuntimeException("secret=must-not-leak");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String generateExpiredToken() {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Date now = new Date();
        Date past = new Date(now.getTime() - 1000 * 60 * 60); // 1 hour ago
        return Jwts.builder()
                .setSubject("testuser")
                .setIssuedAt(past)
                .setExpiration(past)
                .signWith(key)
                .compact();
    }

    @Test
    void whenNoToken_thenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/users/profile"))
               .andExpect(status().isUnauthorized())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(jsonPath("$.status").value(401))
               .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
               .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
               .andExpect(jsonPath("$.correlationId").isString())
               .andExpect(jsonPath("$.retryable").value(false))
               .andExpect(jsonPath("$.path").value("/api/users/profile"));
    }

    @Test
    void whenInvalidToken_thenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/users/profile")
               .header("Authorization", "Bearer invalid.token.here"))
               .andExpect(status().isUnauthorized())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(jsonPath("$.status").value(401))
               .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
               .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
               .andExpect(jsonPath("$.correlationId").isString())
               .andExpect(jsonPath("$.retryable").value(false))
               .andExpect(jsonPath("$.path").value("/api/users/profile"));
    }

    @Test
    void whenExpiredToken_thenReturns401Json() throws Exception {
        String expiredToken = generateExpiredToken();
        mockMvc.perform(get("/api/users/profile")
               .header("Authorization", "Bearer " + expiredToken))
               .andExpect(status().isUnauthorized())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(jsonPath("$.status").value(401))
               .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
               .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
               .andExpect(jsonPath("$.correlationId").isString())
               .andExpect(jsonPath("$.retryable").value(false))
               .andExpect(jsonPath("$.path").value("/api/users/profile"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void whenMissingPermission_thenReturns403Json() throws Exception {
        mockMvc.perform(get("/api/test/permission"))
               .andExpect(status().isForbidden())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(jsonPath("$.status").value(403))
               .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
               .andExpect(jsonPath("$.message").value("Access is denied"))
               .andExpect(jsonPath("$.correlationId").isString())
               .andExpect(jsonPath("$.retryable").value(false))
               .andExpect(jsonPath("$.path").value("/api/test/permission"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void whenMissingFeature_thenReturns403Json() throws Exception {
        mockMvc.perform(get("/api/test/feature"))
               .andExpect(status().isForbidden())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(jsonPath("$.status").value(403))
               .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
               .andExpect(jsonPath("$.message").value("Access is denied"))
               .andExpect(jsonPath("$.correlationId").isString())
               .andExpect(jsonPath("$.retryable").value(false))
               .andExpect(jsonPath("$.path").value("/api/test/feature"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void commonControllerErrorsUseStableEnvelope() throws Exception {
        mockMvc.perform(get("/api/test/invalid-request")
                        .header("X-Correlation-ID", "corr-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request fixture"))
                .andExpect(jsonPath("$.correlationId").value("corr-invalid"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.currentState").value(nullValue()))
                .andExpect(jsonPath("$.path").value("/api/test/invalid-request"));

        mockMvc.perform(get("/api/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.path").value("/api/test/conflict"));

        mockMvc.perform(get("/api/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.path").value("/api/test/not-found"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void unexpectedErrorsAreRedactedAndNotAutomaticallyRetryable() throws Exception {
        mockMvc.perform(get("/api/test/internal-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.path").value("/api/test/internal-error"));
    }
}

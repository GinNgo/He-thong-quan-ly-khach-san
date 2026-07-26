package com.hotel.security;

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

@SpringBootTest
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
               .andExpect(jsonPath("$.path").value("/api/test/feature"));
    }
}

package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.dtos.LoginRequest;
import com.hotel.entities.RefreshTokenSession;
import com.hotel.entities.User;
import com.hotel.repositories.RefreshTokenSessionRepository;
import com.hotel.repositories.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRefreshTokenIntegrationTest {

    private static final String USERNAME = "refresh_http_user";
    private static final String EMAIL = "refresh-http-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenSessionRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.findByUsername(USERNAME).ifPresent(user -> {
            refreshTokenRepository.deleteAll();
            userRepository.delete(user);
        });
        userRepository.flush();
        createUser();
    }

    @Test
    void loginSetsHttpOnlyRefreshCookieAndRefreshRotatesIt() throws Exception {
        String firstCookie = cookieValue(loginAndReadCookie());

        String rotatedCookie = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie(firstCookie))
                        .header("X-Refresh-Request", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(cookie().exists("luxestay_refresh"))
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        assertNotNull(rotatedCookie);
        String rotatedValue = cookieValue(rotatedCookie);
        org.junit.jupiter.api.Assertions.assertNotEquals(firstCookie, rotatedValue);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie(firstCookie))
                        .header("X-Refresh-Request", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie(rotatedValue))
                        .header("X-Refresh-Request", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void refreshRequiresTheSameOriginRequestMarker() throws Exception {
        String cookie = cookieValue(loginAndReadCookie());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(cookie)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFRESH_REQUEST_INVALID"));
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        User user = userRepository.findByUsername(USERNAME).orElseThrow();
        String raw = "expired-refresh-token-" + "x".repeat(24);
        refreshTokenRepository.saveAndFlush(new RefreshTokenSession(
                user,
                "expired-family",
                com.hotel.services.RefreshTokenService.hashToken(raw),
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(60)));
        org.junit.jupiter.api.Assertions.assertTrue(refreshTokenRepository
                .findStoredByTokenHash(com.hotel.services.RefreshTokenService.hashToken(raw)).isPresent());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie(raw))
                        .header("X-Refresh-Request", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    void logoutRevokesRefreshFamilyAndAlreadyIssuedAccessToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andReturn();
        String refresh = cookieValue(login.getResponse().getHeader("Set-Cookie"));
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(refreshCookie(refresh))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Logout-Request", "1"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("luxestay_refresh", 0));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie(refresh))
                        .header("X-Refresh-Request", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    private String loginAndReadCookie() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setUsername(USERNAME);
        login.setPassword("Password@123");
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("luxestay_refresh", true))
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");
    }

    private LoginRequest loginRequest() {
        LoginRequest login = new LoginRequest();
        login.setUsername(USERNAME);
        login.setPassword("Password@123");
        return login;
    }

    private String cookieValue(String header) {
        int start = header.indexOf('=') + 1;
        int end = header.indexOf(';');
        return header.substring(start, end < 0 ? header.length() : end);
    }

    private Cookie refreshCookie(String value) {
        return new Cookie("luxestay_refresh", value);
    }

    private User createUser() {
        User user = new User();
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode("Password@123"));
        user.setStatus("ACTIVE");
        user.setRoles(Set.of());
        return userRepository.saveAndFlush(user);
    }
}

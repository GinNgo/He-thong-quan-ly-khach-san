package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.dtos.LoginRequest;
import com.hotel.entities.EmailVerificationPurpose;
import com.hotel.entities.EmailVerificationToken;
import com.hotel.entities.User;
import com.hotel.repositories.EmailVerificationTokenRepository;
import com.hotel.repositories.RefreshTokenSessionRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = {
                "payment.property.encryption-key=test-property-payment-key",
                "app.mail.email-verification-enabled=false"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationControllerIntegrationTest {

    private static final String USERNAME = "t222-email-user@example.com";
    private static final String EMAIL = "t222-email-user@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationTokenRepository tokenRepository;
    @Autowired private RefreshTokenSessionRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanFixtures() {
        refreshTokenRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith("t222-"))
                .toList());
        userRepository.flush();
    }

    @Test
    void authenticatedUserCanResendWithoutExposingDeliveryConfiguration() throws Exception {
        User user = createUser(false);
        String accessToken = login(user.getUsername());

        mockMvc.perform(post("/api/users/me/email-verification/resend")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.emailSent").value(false))
                .andExpect(jsonPath("$.alreadyVerified").value(false));

        assertEquals(1, tokenRepository.findAll().size());
    }

    @Test
    void publicConfirmationIsOneTimeAndReturnsStableReplayError() throws Exception {
        User user = createUser(false);
        saveToken(user, EmailVerificationPurpose.INITIAL_VERIFICATION, EMAIL, "known-token");

        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"known-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailChanged").value(false));

        assertNotNull(userRepository.findById(user.getId()).orElseThrow().getEmailVerifiedAt());

        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"known-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    void emailChangeRemainsPendingUntilConfirmationAndThenRevokesSession() throws Exception {
        User user = createUser(true);
        String accessToken = login(user.getUsername());

        mockMvc.perform(post("/api/users/me/email-change")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"t222-new@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pendingEmail").value("t222-new@example.com"));

        User pending = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(EMAIL, pending.getEmail());
        assertEquals("t222-new@example.com", pending.getPendingEmail());

        tokenRepository.deleteAll();
        saveToken(pending, EmailVerificationPurpose.EMAIL_CHANGE, "t222-new@example.com", "change-token");
        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"change-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailChanged").value(true))
                .andExpect(jsonPath("$.email").value("t222-new@example.com"));

        User changed = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("t222-new@example.com", changed.getEmail());
        assertEquals("t222-new@example.com", changed.getUsername());
        assertNull(changed.getPendingEmail());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileUpdateCannotBypassVerifiedEmailChangeFlow() throws Exception {
        User user = createUser(true);
        String accessToken = login(user.getUsername());

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated Name",
                                  "email": "t222-bypass@example.com",
                                  "phone": "+84 900 000 000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(EMAIL, unchanged.getEmail());
        assertEquals("Updated Name", unchanged.getFullName());
    }

    private User createUser(boolean verified) {
        User user = new User();
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode("Password@123"));
        user.setFullName("T222 User");
        user.setStatus("ACTIVE");
        user.setRoles(Set.of());
        user.setEmailVerifiedAt(verified ? Instant.now().minusSeconds(60) : null);
        return userRepository.saveAndFlush(user);
    }

    private void saveToken(
            User user,
            EmailVerificationPurpose purpose,
            String targetEmail,
            String rawToken) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTargetEmail(targetEmail);
        token.setTokenHash(PasswordResetService.hashToken(rawToken));
        token.setRequestedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(300));
        token.setRequestIp("127.0.0.1");
        tokenRepository.saveAndFlush(token);
    }

    private String login(String username) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword("Password@123");
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}

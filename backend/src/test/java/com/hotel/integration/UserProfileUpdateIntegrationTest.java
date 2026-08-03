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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class UserProfileUpdateIntegrationTest {

    private static final String EMAIL = "t224-profile@example.com";
    private static final String PASSWORD = "Password@123";

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
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith("t224-"))
                .toList());
        userRepository.flush();
    }

    @Test
    void profileUpdateNormalizesSafeFieldsAndCannotBypassVerifiedEmailChange() throws Exception {
        User user = createUser(EMAIL);
        String accessToken = login(EMAIL);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "  Nguyen    Van A  ",
                                  "email": "t224-bypass@example.com",
                                  "phone": "  +84   900 000 000  ",
                                  "avatarUrl": "  /api/public/uploads/avatar-41.webp  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.username").value(EMAIL))
                .andExpect(jsonPath("$.phone").value("+84 900 000 000"))
                .andExpect(jsonPath("$.avatarUrl").value("/api/public/uploads/avatar-41.webp"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(EMAIL, updated.getEmail());
        assertEquals(EMAIL, updated.getUsername());
    }

    @Test
    void profileUpdateRejectsInvalidFieldsBeforeMutation() throws Exception {
        User user = createUser(EMAIL);
        String accessToken = login(EMAIL);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "   ",
                                  "email": "not-an-email",
                                  "phone": "call me maybe",
                                  "avatarUrl": "/api/public/uploads/avatar.png"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("T224 Profile", unchanged.getFullName());
        assertNull(unchanged.getAvatarUrl());
    }

    @Test
    void profileUpdateRejectsUnsafeAvatarUrlWithoutMutation() throws Exception {
        User user = createUser(EMAIL);
        String accessToken = login(EMAIL);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Changed Name",
                                  "email": "t224-profile@example.com",
                                  "phone": "+84 900 000 000",
                                  "avatarUrl": "javascript:alert(1)"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("T224 Profile", unchanged.getFullName());
        assertNull(unchanged.getAvatarUrl());
    }

    @Test
    void duplicateEmailChangeReturnsConflictAndLeavesCurrentIdentityUntouched() throws Exception {
        User user = createUser(EMAIL);
        createUser("t224-taken@example.com");
        String accessToken = login(EMAIL);

        mockMvc.perform(post("/api/users/me/email-change")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"T224-TAKEN@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IDENTITY_CONFLICT"));

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(EMAIL, unchanged.getEmail());
        assertEquals(EMAIL, unchanged.getUsername());
        assertNull(unchanged.getPendingEmail());
    }

    @Test
    void verifiedEmailChangeMovesEmailBasedLoginIdentity() throws Exception {
        User user = createUser(EMAIL);
        String accessToken = login(EMAIL);

        mockMvc.perform(post("/api/users/me/email-change")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"t224-new@example.com\"}"))
                .andExpect(status().isAccepted());

        tokenRepository.deleteAll();
        saveToken(userRepository.findById(user.getId()).orElseThrow(), "known-t224-token");
        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"known-t224-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("t224-new@example.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("t224-new@example.com")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL)))
                .andExpect(status().isUnauthorized());

        User changed = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("t224-new@example.com", changed.getEmail());
        assertEquals("t224-new@example.com", changed.getUsername());
    }

    private User createUser(String email) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("T224 Profile");
        user.setStatus("ACTIVE");
        user.setRoles(Set.of());
        user.setEmailVerifiedAt(Instant.now().minusSeconds(60));
        return userRepository.saveAndFlush(user);
    }

    private void saveToken(User user, String rawToken) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setPurpose(EmailVerificationPurpose.EMAIL_CHANGE);
        token.setTargetEmail("t224-new@example.com");
        token.setTokenHash(PasswordResetService.hashToken(rawToken));
        token.setRequestedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(300));
        token.setRequestIp("127.0.0.1");
        tokenRepository.saveAndFlush(token);
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String loginBody(String username) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(PASSWORD);
        return objectMapper.writeValueAsString(request);
    }
}

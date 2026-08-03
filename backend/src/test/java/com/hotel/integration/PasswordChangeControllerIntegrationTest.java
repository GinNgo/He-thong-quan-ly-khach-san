package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordChangeControllerIntegrationTest {

    private static final String USERNAME = "password-change-user";
    private static final String EMAIL = "password-change@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User account;

    @BeforeEach
    void setUp() {
        userRepository.findByUsername(USERNAME).ifPresent(userRepository::delete);
        userRepository.flush();

        User user = new User();
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setFullName("Password Change User");
        user.setPasswordHash(passwordEncoder.encode("Current@123"));
        user.setStatus("ACTIVE");
        account = userRepository.saveAndFlush(user);
    }

    @Test
    void changesPasswordAndRevokesTheAuthenticatedSession() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .with(user(principal(account)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Current@123",
                                "newPassword", "Changed@123"))))
                .andExpect(status().isNoContent());

        User updated = userRepository.findById(account.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("Changed@123", updated.getPasswordHash()));
        assertNotNull(updated.getAuthRevokedAt());
    }

    @Test
    void wrongCurrentPasswordReturnsStableErrorWithoutMutation() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .with(user(principal(account)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Wrong@123",
                                "newPassword", "Changed@123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        User unchanged = userRepository.findById(account.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("Current@123", unchanged.getPasswordHash()));
    }

    @Test
    void invalidNewPasswordReturnsFieldValidationError() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .with(user(principal(account)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Current@123",
                                "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    private CustomUserDetails principal(User user) {
        return new CustomUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                new HashMap<>(),
                user.getId(),
                null,
                new HashMap<>());
    }
}

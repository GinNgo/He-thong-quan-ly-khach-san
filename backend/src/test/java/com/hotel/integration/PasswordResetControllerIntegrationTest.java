package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.entities.User;
import com.hotel.repositories.PasswordResetTokenRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.mail.password-reset-enabled=false")
class PasswordResetControllerIntegrationTest {

    private static final String EMAIL = "password-reset-http@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.findByEmail(EMAIL).ifPresent(user -> userRepository.delete(user));
        userRepository.flush();
    }

    @Test
    void forgotPasswordUsesTheSameAcceptedResponseForKnownAndUnknownEmails() throws Exception {
        User user = new User();
        user.setUsername("password-reset-http-user");
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode("Password@123"));
        user.setStatus("ACTIVE");
        userRepository.saveAndFlush(user);

        String known = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "unknown-reset@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(equalTo(
                        objectMapper.readTree(known).get("message").asText())));
    }
}

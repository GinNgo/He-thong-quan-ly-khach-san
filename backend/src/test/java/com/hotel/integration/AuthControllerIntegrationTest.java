package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.dtos.LoginRequest;
import com.hotel.dtos.RegisterRequest;
import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerIntegrationTest {

    private static final String REGISTERED_USERNAME = "auth_http_registered_user";
    private static final String WRONG_PASSWORD_USERNAME = "auth_http_wrong_password";
    private static final String INVALID_USERNAME = "abc";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        List.of(REGISTERED_USERNAME, WRONG_PASSWORD_USERNAME, INVALID_USERNAME).forEach(username ->
                userRepository.findByUsername(username).ifPresent(userRepository::delete));
        userRepository.flush();
    }

    @Test
    void testRegisterAndLogin() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(REGISTERED_USERNAME);
        registerRequest.setPassword("Password@123");
        registerRequest.setEmail("auth-http-registered@example.com");
        registerRequest.setFullName("Test Integration User");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully!"))
                .andExpect(jsonPath("$.welcomeEmailSent").value(false));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(REGISTERED_USERNAME);
        loginRequest.setPassword("Password@123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.username").value(REGISTERED_USERNAME))
                .andExpect(jsonPath("$.userId").isNumber());
    }

    @Test
    void testLogin_Failure_WrongPassword() throws Exception {
        User user = new User();
        user.setUsername(WRONG_PASSWORD_USERNAME);
        user.setPasswordHash(passwordEncoder.encode("Correct@123"));
        user.setEmail("auth-http-wrong-password@example.com");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(WRONG_PASSWORD_USERNAME);
        loginRequest.setPassword("Wrong@123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void testRegister_ValidationFailure_DoesNotPersist() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(INVALID_USERNAME);
        request.setPassword("short");
        request.setEmail("invalid-email");
        request.setFullName("");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        org.junit.jupiter.api.Assertions.assertFalse(userRepository.existsByUsername(INVALID_USERNAME));
    }

    @Test
    void testProtectedEndpoint_RejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}

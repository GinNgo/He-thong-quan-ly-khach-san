package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerIntegrationTest {

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
        // Do not delete all to avoid FK constraint violations
    }

    @Test
    void testRegisterAndLogin() throws Exception {
        // 1. Register a new user
        RegisterRequest registerRequest = new RegisterRequest();
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        registerRequest.setUsername("testuser_int_" + uniqueSuffix);
        registerRequest.setPassword("Password@123");
        registerRequest.setEmail("test_int_" + uniqueSuffix + "@example.com");
        registerRequest.setFullName("Test Integration User");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().string("User registered successfully!"));

        // 2. Login with the new user
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser_int_" + uniqueSuffix);
        loginRequest.setPassword("Password@123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.username").value("testuser_int_" + uniqueSuffix));
    }

    @Test
    void testLogin_Failure_WrongPassword() throws Exception {
        // Setup user
        User user = new User();
        user.setUsername("wrongpassuser");
        user.setPasswordHash(passwordEncoder.encode("Correct@123"));
        user.setEmail("wrongpass@example.com");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        // Attempt login with wrong password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("wrongpassuser");
        loginRequest.setPassword("Wrong@123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Sai tài khoản hoặc mật khẩu"));
    }
}

package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.UserRequest;
import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private CustomUserDetails getAdminUser() {
        return new CustomUserDetails(
                "admin",
                "hash",
                Set.of(new SimpleGrantedAuthority("ADMIN")),
                new HashMap<>(),
                1L,
                null,
                new HashMap<>()
        );
    }

    private CustomUserDetails getNormalUser() {
        return new CustomUserDetails(
                "user",
                "hash",
                new HashSet<>(),
                new HashMap<>(),
                2L,
                null,
                new HashMap<>()
        );
    }

    @Test
    void getAllUsers_AsAdmin_ShouldReturnOk() throws Exception {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hash");
        testUser.setFullName("Test User");
        testUser.setStatus("ACTIVE");
        userRepository.saveAndFlush(testUser);

        mockMvc.perform(get("/api/users")
                        .with(user(getAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    void getAllUsers_AsNormalUser_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(user(getNormalUser()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_AsAdmin_ShouldReturnOk() throws Exception {
        UserRequest request = new UserRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@example.com");
        request.setPassword("password123");
        request.setFullName("New User");
        request.setPhone("0123456789");
        request.setStatus("ACTIVE");
        request.setRoleIds(Collections.emptySet());

        mockMvc.perform(post("/api/users")
                        .with(user(getAdminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("newuser")));
    }
}

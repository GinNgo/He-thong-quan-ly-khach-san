package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.dtos.RegisterRequest;
import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CredentialRegistrationIntegrationTest {

    private static final String USERNAME = "t214-auth-user";
    private static final String EMAIL = "t214-auth-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getUsername() != null && user.getUsername().startsWith("t214-"))
                .toList());
        userRepository.flush();
    }

    @Test
    void registrationNormalizesIdentityAndDisplayFields() throws Exception {
        RegisterRequest request = request("T214-Normalized", "T214.User@Example.com", "  Guest   Name  ");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User user = userRepository.findByUsername("t214-normalized").orElseThrow();
        assertEquals("t214.user@example.com", user.getEmail());
        assertEquals("Guest Name", user.getFullName());
        assertNotNull(user.getVersion());
    }

    @Test
    void duplicateUsernameReturnsStableConflictAndFieldError() throws Exception {
        register(request(USERNAME, EMAIL, "First User"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("T214-AUTH-USER", "other@example.com", "Second User"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors.username").value("Username is already registered."))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void duplicateEmailReturnsStableConflictAndFieldError() throws Exception {
        register(request(USERNAME, EMAIL, "First User"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("other-t214-user", "T214-AUTH-USER@EXAMPLE.COM", "Second User"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email is already registered."));
    }

    @Test
    void concurrentSameIdentityCreatesOneAccountAndOneConflict() throws Exception {
        RegisterRequest request = request("T214-Concurrent", "T214.Concurrent@Example.com", "Concurrent User");
        String body = objectMapper.writeValueAsString(request);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> operation = () -> mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            List<Future<Integer>> futures = executor.invokeAll(List.of(operation, operation));
            List<Integer> statuses = futures.stream().map(this::get).toList();

            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count());
            assertTrue(userRepository.existsByUsernameIgnoreCase("t214-concurrent"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void malformedCredentialFailsValidationBeforePersistence() throws Exception {
        RegisterRequest request = request("bad user", "not-an-email", "");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").exists());

        assertTrue(userRepository.findByUsername("bad user").isEmpty());
    }

    private void register(RegisterRequest request) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private RegisterRequest request(String username, String email, String fullName) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword("Password@123");
        request.setEmail(email);
        request.setFullName(fullName);
        request.setPhone("+84 901 234 567");
        return request;
    }

    private Integer get(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("Concurrent registration operation failed", exception);
        }
    }
}

package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.dtos.LoginRequest;
import com.hotel.entities.User;
import com.hotel.repositories.RefreshTokenSessionRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AvatarUploadIntegrationTest {

    private static final String EMAIL = "t225-avatar@example.com";
    private static final String OTHER_EMAIL = "t225-other@example.com";
    private static final String PASSWORD = "Password@123";
    private static final Path UPLOAD_DIRECTORY = createUploadDirectory();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefreshTokenSessionRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenSessionRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("upload.path", () -> UPLOAD_DIRECTORY.toString());
        registry.add("upload.avatar.max-width", () -> 128);
        registry.add("upload.avatar.max-height", () -> 128);
        registry.add("upload.avatar.max-pixels", () -> 16_384);
    }

    @BeforeEach
    void cleanFixtures() throws IOException {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith("t225-"))
                .toList());
        userRepository.flush();
        try (var files = Files.list(UPLOAD_DIRECTORY)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    @AfterAll
    static void deleteUploadDirectory() throws IOException {
        if (!Files.exists(UPLOAD_DIRECTORY)) return;
        try (var paths = Files.walk(UPLOAD_DIRECTORY)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void anonymousUploadIsDenied() throws Exception {
        mockMvc.perform(multipart("/api/uploads/image")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", png(2, 2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUploadUpdatesOnlyCurrentUserAndDeletesReplacement() throws Exception {
        User owner = createUser(EMAIL);
        User other = createUser(OTHER_EMAIL);
        String accessToken = login(EMAIL);

        String firstUrl = upload(accessToken, png(2, 2));
        Path firstFile = pathFromUrl(firstUrl);
        assertTrue(Files.isRegularFile(firstFile));

        String secondUrl = upload(accessToken, png(3, 2));

        User updatedOwner = userRepository.findById(owner.getId()).orElseThrow();
        User unchangedOther = userRepository.findById(other.getId()).orElseThrow();
        assertEquals(secondUrl, updatedOwner.getAvatarUrl());
        assertNull(unchangedOther.getAvatarUrl());
        assertFalse(Files.exists(firstFile));
        assertTrue(Files.isRegularFile(pathFromUrl(secondUrl)));

        String filename = secondUrl.substring(secondUrl.lastIndexOf('/') + 1);
        mockMvc.perform(get("/api/public/uploads/{filename}", filename))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void invalidSignatureReturnsStableSafeContractWithoutMutation() throws Exception {
        User owner = createUser(EMAIL);
        String accessToken = login(EMAIL);

        mockMvc.perform(multipart("/api/uploads/image")
                        .file(new MockMultipartFile(
                                "file", "avatar.png", "image/png", "not-an-image".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("X-Correlation-ID", "t225-invalid-signature"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("X-Correlation-ID", "t225-invalid-signature"))
                .andExpect(jsonPath("$.code").value("AVATAR_SIGNATURE_INVALID"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.message").value(
                        "The uploaded file is not a valid supported image."));

        assertNull(userRepository.findById(owner.getId()).orElseThrow().getAvatarUrl());
        try (var files = Files.list(UPLOAD_DIRECTORY)) {
            assertEquals(0, files.filter(Files::isRegularFile).count());
        }
    }

    private String upload(String accessToken, byte[] bytes) throws Exception {
        String response = mockMvc.perform(multipart("/api/uploads/image")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", bytes))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("url").asText();
    }

    private User createUser(String email) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("T225 Avatar");
        user.setStatus("ACTIVE");
        user.setRoles(Set.of());
        user.setEmailVerifiedAt(Instant.now().minusSeconds(60));
        return userRepository.saveAndFlush(user);
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String loginBody(String username) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(PASSWORD);
        return objectMapper.writeValueAsString(request);
    }

    private Path pathFromUrl(String url) {
        return UPLOAD_DIRECTORY.resolve(url.substring(url.lastIndexOf('/') + 1));
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG test writer unavailable.");
        }
        return output.toByteArray();
    }

    private static Path createUploadDirectory() {
        try {
            return Files.createTempDirectory("t225-avatar-upload-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

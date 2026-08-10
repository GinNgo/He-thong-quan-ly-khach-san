package com.hotel.services;

import com.hotel.entities.User;
import com.hotel.exceptions.AvatarUploadException;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileUploadServiceTest {

    @TempDir
    Path uploadDirectory;

    private UserRepository userRepository;
    private User user;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        user = new User();
        user.setId(41L);
        user.setUsername("avatar-owner@example.com");
        user.setEmail("avatar-owner@example.com");
        user.setPasswordHash("hash");
        when(userRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = newService(5L * 1024 * 1024, 4096, 4096, 16_777_216L);
    }

    @Test
    void storesSignatureVerifiedImageUnderUserOwnedRandomName() throws Exception {
        MultipartFile upload = new MockMultipartFile(
                "file", "profile.txt", "image/png", png(3, 2));

        FileUploadService.StoredAvatar result = service.replaceAvatar(41L, upload);

        assertEquals("image/png", result.contentType());
        assertEquals(3, result.width());
        assertEquals(2, result.height());
        assertTrue(result.url().matches(
                "^/api/public/uploads/avatar-41-[0-9a-f-]+\\.png$"));
        assertEquals(result.url(), user.getAvatarUrl());
        assertTrue(Files.isRegularFile(pathFromUrl(result.url())));
    }

    @Test
    void rejectsSpoofedMimeWithoutWritingAFile() throws Exception {
        MultipartFile upload = new MockMultipartFile(
                "file", "profile.png", "image/png", "not-an-image".getBytes());

        AvatarUploadException exception = assertThrows(
                AvatarUploadException.class,
                () -> service.replaceAvatar(41L, upload));

        assertEquals("AVATAR_SIGNATURE_INVALID", exception.code());
        assertEquals(0, regularFileCount());
    }

    @Test
    void rejectsDeclaredContentTypeThatDoesNotMatchSignature() throws Exception {
        MultipartFile upload = new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", png(2, 2));

        AvatarUploadException exception = assertThrows(
                AvatarUploadException.class,
                () -> service.replaceAvatar(41L, upload));

        assertEquals("AVATAR_CONTENT_TYPE_MISMATCH", exception.code());
        assertEquals(0, regularFileCount());
    }

    @Test
    void rejectsImagesOutsideConfiguredDimensionBounds() throws Exception {
        service = newService(5L * 1024 * 1024, 2, 2, 4);
        MultipartFile upload = new MockMultipartFile(
                "file", "profile.png", "image/png", png(3, 2));

        AvatarUploadException exception = assertThrows(
                AvatarUploadException.class,
                () -> service.replaceAvatar(41L, upload));

        assertEquals("AVATAR_DIMENSIONS_INVALID", exception.code());
        assertEquals(0, regularFileCount());
    }

    @Test
    void deletesReplacedManagedAvatarAfterSuccessfulUpdate() throws Exception {
        Path previous = uploadDirectory.resolve("legacy-avatar.png");
        Files.write(previous, png(1, 1));
        user.setAvatarUrl("/api/public/uploads/legacy-avatar.png");

        FileUploadService.StoredAvatar result = service.replaceAvatar(
                41L,
                new MockMultipartFile("file", "new.png", "image/png", png(2, 2)));

        assertFalse(Files.exists(previous));
        assertTrue(Files.exists(pathFromUrl(result.url())));
    }

    @Test
    void deletesNewFileWhenDatabaseAssociationFails() throws Exception {
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> service.replaceAvatar(
                        41L,
                        new MockMultipartFile("file", "new.png", "image/png", png(2, 2))));

        assertEquals(0, regularFileCount());
    }

    @Test
    void refusesTraversalAndMissingPublicFilesWithoutLeakingPaths() {
        AvatarUploadException traversal = assertThrows(
                AvatarUploadException.class,
                () -> service.loadImageResource("../application.yml"));
        AvatarUploadException missing = assertThrows(
                AvatarUploadException.class,
                () -> service.loadImageResource("missing.png"));

        assertEquals("AVATAR_NOT_FOUND", traversal.code());
        assertEquals("AVATAR_NOT_FOUND", missing.code());
    }

    private FileUploadService newService(
            long maxBytes,
            int maxWidth,
            int maxHeight,
            long maxPixels) {
        FileUploadService candidate = new FileUploadService(
                userRepository,
                uploadDirectory.toString(),
                maxBytes,
                maxWidth,
                maxHeight,
                maxPixels);
        candidate.init();
        return candidate;
    }

    private Path pathFromUrl(String url) {
        return uploadDirectory.resolve(url.substring(url.lastIndexOf('/') + 1));
    }

    private long regularFileCount() throws Exception {
        try (var files = Files.list(uploadDirectory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}

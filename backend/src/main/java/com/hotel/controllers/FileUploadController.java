package com.hotel.controllers;

import com.hotel.dtos.AvatarUploadResponse;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/uploads/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AvatarUploadResponse> uploadImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        if (userDetails == null || userDetails.getUserId() == null) {
            throw com.hotel.exceptions.AvatarUploadException.userNotFound();
        }
        FileUploadService.StoredAvatar avatar = fileUploadService.replaceAvatar(userDetails.getUserId(), file);
        return ResponseEntity.ok(new AvatarUploadResponse(
                avatar.url(), avatar.contentType(), avatar.width(), avatar.height()));
    }

    @GetMapping("/public/uploads/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> serveFile(@PathVariable String filename) {
        FileUploadService.StoredImageResource image = fileUploadService.loadImageResource(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.resource());
    }
}

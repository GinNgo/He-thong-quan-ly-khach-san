package com.hotel.controllers;

import com.hotel.dtos.PropertyGalleryImageDTO;
import com.hotel.dtos.PropertyGalleryOrderRequest;
import com.hotel.dtos.PropertyImageLinkRequest;
import com.hotel.services.PropertyGalleryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/gallery")
@PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER','SUPER_ADMIN','ADMIN')")
@RequiredArgsConstructor
@Validated
public class PropertyGalleryController {

    private final PropertyGalleryService propertyGalleryService;

    @GetMapping
    public ResponseEntity<List<PropertyGalleryImageDTO>> list(@PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyGalleryService.list(propertyId));
    }

    @PostMapping("/links")
    public ResponseEntity<PropertyGalleryImageDTO> addLink(
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyImageLinkRequest request) {
        return ResponseEntity.ok(propertyGalleryService.addLink(propertyId, request));
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PropertyGalleryImageDTO> upload(
            @PathVariable Long propertyId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 255) String altTextVi,
            @RequestParam(required = false) @Size(max = 255) String altTextEn,
            @RequestParam(defaultValue = "false") boolean primary) {
        return ResponseEntity.ok(propertyGalleryService.upload(
                propertyId, file, altTextVi, altTextEn, primary));
    }

    @PutMapping("/order")
    public ResponseEntity<List<PropertyGalleryImageDTO>> reorder(
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyGalleryOrderRequest request) {
        return ResponseEntity.ok(propertyGalleryService.reorder(propertyId, request));
    }

    @PutMapping("/images/{imageId}/primary")
    public ResponseEntity<PropertyGalleryImageDTO> setPrimary(
            @PathVariable Long propertyId,
            @PathVariable Long imageId) {
        return ResponseEntity.ok(propertyGalleryService.setPrimary(propertyId, imageId));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<List<PropertyGalleryImageDTO>> delete(
            @PathVariable Long propertyId,
            @PathVariable Long imageId) {
        return ResponseEntity.ok(propertyGalleryService.delete(propertyId, imageId));
    }
}

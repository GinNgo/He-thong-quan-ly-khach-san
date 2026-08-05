package com.hotel.controllers;

import com.hotel.dtos.AmenityAssignmentRequest;
import com.hotel.dtos.AmenityDTO;
import com.hotel.dtos.AmenityUpsertRequest;
import com.hotel.services.AmenityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AmenityController {

    private final AmenityService amenityService;

    @GetMapping("/public/amenities")
    public ResponseEntity<List<AmenityDTO>> publicCatalog() {
        return ResponseEntity.ok(amenityService.activeCatalog());
    }

    @GetMapping("/admin/amenities")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<List<AmenityDTO>> managementCatalog() {
        return ResponseEntity.ok(amenityService.managementCatalog());
    }

    @PostMapping("/admin/amenities")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<AmenityDTO> create(@Valid @RequestBody AmenityUpsertRequest request) {
        return ResponseEntity.ok(amenityService.create(request));
    }

    @PutMapping("/admin/amenities/{amenityId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<AmenityDTO> update(
            @PathVariable Long amenityId,
            @Valid @RequestBody AmenityUpsertRequest request) {
        return ResponseEntity.ok(amenityService.update(amenityId, request));
    }

    @PostMapping("/admin/amenities/{amenityId}/deactivate")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<AmenityDTO> deactivate(@PathVariable Long amenityId) {
        return ResponseEntity.ok(amenityService.deactivate(amenityId));
    }

    @GetMapping("/v1/properties/{propertyId}/amenities")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<AmenityDTO>> propertyAmenities(@PathVariable Long propertyId) {
        return ResponseEntity.ok(amenityService.propertyAmenities(propertyId));
    }

    @PutMapping("/v1/properties/{propertyId}/amenities")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<AmenityDTO>> replacePropertyAmenities(
            @PathVariable Long propertyId,
            @Valid @RequestBody AmenityAssignmentRequest request) {
        return ResponseEntity.ok(amenityService.replacePropertyAmenities(propertyId, request));
    }

    @GetMapping("/v1/room-types/{roomTypeId}/amenities")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<AmenityDTO>> roomTypeAmenities(@PathVariable Long roomTypeId) {
        return ResponseEntity.ok(amenityService.roomTypeAmenities(roomTypeId));
    }

    @PutMapping("/v1/room-types/{roomTypeId}/amenities")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<AmenityDTO>> replaceRoomTypeAmenities(
            @PathVariable Long roomTypeId,
            @Valid @RequestBody AmenityAssignmentRequest request) {
        return ResponseEntity.ok(amenityService.replaceRoomTypeAmenities(roomTypeId, request));
    }
}

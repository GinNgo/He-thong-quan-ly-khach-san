package com.hotel.controllers;

import com.hotel.dtos.PlacementDecisionRequest;
import com.hotel.dtos.SponsoredPlacementDTO;
import com.hotel.dtos.SponsoredPlacementRequest;
import com.hotel.services.SponsoredPlacementManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sponsored-placements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN','PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER')")
public class SponsoredPlacementController {

    private final SponsoredPlacementManagementService placementService;

    @GetMapping
    public ResponseEntity<List<SponsoredPlacementDTO>> list(@RequestParam(required = false) Long hotelId) {
        return ResponseEntity.ok(placementService.list(hotelId));
    }

    @PostMapping
    public ResponseEntity<SponsoredPlacementDTO> create(@Valid @RequestBody SponsoredPlacementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(placementService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SponsoredPlacementDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody SponsoredPlacementRequest request) {
        return ResponseEntity.ok(placementService.update(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<SponsoredPlacementDTO> approve(@PathVariable Long id) {
        return ResponseEntity.ok(placementService.approve(id));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<SponsoredPlacementDTO> pause(@PathVariable Long id) {
        return ResponseEntity.ok(placementService.pause(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<SponsoredPlacementDTO> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PlacementDecisionRequest request) {
        return ResponseEntity.ok(placementService.reject(id, request == null ? null : request.reason()));
    }
}


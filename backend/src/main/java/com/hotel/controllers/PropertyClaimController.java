package com.hotel.controllers;

import com.hotel.entities.PropertyClaimRequest;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.services.PropertyClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PropertyClaimController {

    private final PropertyClaimService claimService;
    private final PropertyClaimRequestRepository claimRepository;

    @PostMapping("/properties/{propertyId}/claim")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> requestClaim(@PathVariable Long propertyId, @RequestBody Map<String, String> payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Assuming CustomUserDetails or simply getting user ID from context. For simplicity, passing mock ID if not available.
        // In real app, extract user ID correctly.
        Long userId = 2L; // Replace with actual user ID extraction logic.
        
        String verificationMethod = payload.get("verificationMethod");
        String verificationData = payload.get("verificationData");
        String note = payload.get("note");

        PropertyClaimRequest claim = claimService.requestClaim(propertyId, userId, verificationMethod, verificationData, note);
        return ResponseEntity.ok(claim);
    }

    @GetMapping("/admin/property-claims")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_VIEW') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<PropertyClaimRequest>> getAllClaims(@RequestParam(required = false) String status, Pageable pageable) {
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(claimRepository.findByStatus(status, pageable));
        }
        return ResponseEntity.ok(claimRepository.findAll(pageable));
    }

    @PostMapping("/admin/property-claims/{id}/approve")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_APPROVE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> approveClaim(@PathVariable Long id) {
        Long adminUserId = 1L; // Replace with real admin user ID
        PropertyClaimRequest claim = claimService.approveClaim(id, adminUserId);
        return ResponseEntity.ok(claim);
    }

    @PostMapping("/admin/property-claims/{id}/reject")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_APPROVE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> rejectClaim(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        Long adminUserId = 1L; // Replace with real admin user ID
        String reason = payload.get("reason");
        PropertyClaimRequest claim = claimService.rejectClaim(id, adminUserId, reason);
        return ResponseEntity.ok(claim);
    }
}

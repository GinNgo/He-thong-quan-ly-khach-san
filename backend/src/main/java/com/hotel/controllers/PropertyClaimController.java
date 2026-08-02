package com.hotel.controllers;

import com.hotel.entities.PropertyClaimRequest;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.PropertyClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<?> requestClaim(
            @PathVariable Long propertyId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String verificationMethod = payload.get("verificationMethod");
        String verificationData = payload.get("verificationData");
        String note = payload.get("note");

        PropertyClaimRequest claim = claimService.requestClaim(
                propertyId,
                principal.getUserId(),
                verificationMethod,
                verificationData,
                note);
        return ResponseEntity.ok(claim);
    }

    @GetMapping("/admin/property-claims")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_VIEW') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Page<PropertyClaimRequest>> getAllClaims(@RequestParam(required = false) String status, Pageable pageable) {
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(claimRepository.findByStatus(status, pageable));
        }
        return ResponseEntity.ok(claimRepository.findAll(pageable));
    }

    @PostMapping("/admin/property-claims/{id}/approve")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_APPROVE') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<?> approveClaim(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        PropertyClaimRequest claim = claimService.approveClaim(id, principal.getUserId());
        return ResponseEntity.ok(claim);
    }

    @PostMapping("/admin/property-claims/{id}/reject")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_APPROVE') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<?> rejectClaim(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String reason = payload.get("reason");
        PropertyClaimRequest claim = claimService.rejectClaim(id, principal.getUserId(), reason);
        return ResponseEntity.ok(claim);
    }

    @PostMapping("/property-claims/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancelClaim(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(claimService.cancelClaim(id, principal.getUserId()));
    }
}

package com.hotel.controllers;

import com.hotel.dtos.PartnerRegistrationRequest;
import com.hotel.dtos.PartnerRegistrationResponse;
import com.hotel.dtos.PartnerConversionRequest;
import com.hotel.dtos.PartnerRegistrationStatusResponse;
import com.hotel.dtos.PropertyApprovalSubmissionResponse;
import com.hotel.dtos.PropertyReviewHistoryItem;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.PropertyApprovalWorkflowService;
import com.hotel.services.PropertyRegistrationService;
import com.hotel.services.PropertyReviewHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping({"/api/partner", "/api/v1/partner"})
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class PropertyRegistrationController {

    private final PropertyRegistrationService registrationService;
    private final PropertyApprovalWorkflowService propertyApprovalWorkflowService;
    private final PropertyReviewHistoryService propertyReviewHistoryService;

    @PostMapping("/register")
    public ResponseEntity<PartnerRegistrationResponse> registerProperty(
            @Valid @RequestBody PartnerRegistrationRequest request,
            Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            throw new AccessDeniedException("Authenticated partner conversion uses a separate workflow.");
        }
        return ResponseEntity.status(201).body(registrationService.registerAnonymousPartner(request));
    }

    @PostMapping("/convert")
    public ResponseEntity<PartnerRegistrationResponse> convertExistingCustomer(
            @Valid @RequestBody PartnerConversionRequest request,
            Authentication authentication) {
        CustomUserDetails userDetails = requireAuthoritativePrincipal(authentication);
        return ResponseEntity.status(201)
                .body(registrationService.convertExistingCustomer(userDetails.getUserId(), request));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/properties/{propertyId}/submit")
    public ResponseEntity<PropertyApprovalSubmissionResponse> submitProperty(
            @PathVariable Long propertyId,
            Authentication authentication) {
        CustomUserDetails userDetails = requireAuthoritativePrincipal(authentication);
        return ResponseEntity.ok(propertyApprovalWorkflowService.submitDraft(userDetails.getUserId(), propertyId));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/registration-status")
    public ResponseEntity<PartnerRegistrationStatusResponse> registrationStatus(Authentication authentication) {
        CustomUserDetails userDetails = requireAuthoritativePrincipal(authentication);
        return ResponseEntity.ok(registrationService.registrationStatus(userDetails.getUserId()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/properties/{propertyId}/history")
    public ResponseEntity<List<PropertyReviewHistoryItem>> propertyHistory(
            @PathVariable Long propertyId,
            Authentication authentication) {
        CustomUserDetails userDetails = requireAuthoritativePrincipal(authentication);
        return ResponseEntity.ok(propertyReviewHistoryService.ownerHistory(
                userDetails.getUserId(), propertyId));
    }

    private CustomUserDetails requireAuthoritativePrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("Authoritative authenticated account context is required.");
        }
        return userDetails;
    }

}

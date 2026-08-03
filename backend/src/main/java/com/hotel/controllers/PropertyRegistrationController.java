package com.hotel.controllers;

import com.hotel.dtos.PartnerRegistrationRequest;
import com.hotel.dtos.PartnerRegistrationResponse;
import com.hotel.services.PropertyRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/partner", "/api/v1/partner"})
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class PropertyRegistrationController {

    private final PropertyRegistrationService registrationService;

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

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/registration-status")
    public ResponseEntity<?> registrationStatus(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(registrationService.registrationStatus(authentication.getName()));
    }

}

package com.hotel.controllers;

import com.hotel.services.PropertyRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.Data;

@RestController
@RequestMapping({"/api/partner", "/api/v1/partner"})
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class PropertyRegistrationController {

    private final PropertyRegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<?> registerProperty(@RequestBody PartnerRegisterRequest request,
                                              org.springframework.security.core.Authentication authentication) {
        try {
            registrationService.registerPropertyOwner(
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.getPhone(),
                request.getPropertyName(),
                request.getPropertyAddress(),
                authentication == null ? null : authentication.getName()
            );
            return ResponseEntity.ok().body("Registration successful. Please login.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/registration-status")
    public ResponseEntity<?> registrationStatus(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(registrationService.registrationStatus(authentication.getName()));
    }

    @Data
    public static class PartnerRegisterRequest {
        private String email;
        private String password;
        private String fullName;
        private String phone;
        private String propertyName;
        private String propertyAddress;
    }
}

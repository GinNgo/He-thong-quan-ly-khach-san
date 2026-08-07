package com.hotel.controllers;

import com.hotel.dtos.AccountSubscriptionDTO;
import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.SubscriptionFeatureService;
import com.hotel.services.SubscriptionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionFeatureService featureService;
    private final SubscriptionCatalogService catalogService;

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        return ResponseEntity.ok(catalogService.getActivePlans());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<List<AccountSubscriptionDTO>> getMySubscriptions(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(catalogService.getSubscriptions(userDetails.getUserId()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/features")
    public ResponseEntity<Map<String, Integer>> getMyFeatures(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(featureService.getActiveFeaturesForUser(userDetails.getUserId()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/usage")
    public ResponseEntity<SubscriptionUsageDTO> getMyUsage(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(catalogService.getUsage(userDetails.getUserId()));
    }
}

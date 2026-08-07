package com.hotel.controllers;

import com.hotel.dtos.AccountSubscriptionDTO;
import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
<<<<<<< HEAD
=======
import com.hotel.security.CustomUserDetails;
import com.hotel.services.SubscriptionFeatureService;
>>>>>>> codex/ui-functional-audit-polish
import com.hotel.services.SubscriptionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

<<<<<<< HEAD
=======
    private final SubscriptionFeatureService featureService;
>>>>>>> codex/ui-functional-audit-polish
    private final SubscriptionCatalogService catalogService;

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        return ResponseEntity.ok(catalogService.getActivePlans());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
<<<<<<< HEAD
    public ResponseEntity<AccountSubscriptionDTO> getCurrentSubscription(
            @RequestParam Long targetHotelId) {
        return ResponseEntity.ok(catalogService.getCurrent(targetHotelId));
=======
    public ResponseEntity<List<AccountSubscriptionDTO>> getMySubscriptions(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(catalogService.getSubscriptions(userDetails.getUserId()));
>>>>>>> codex/ui-functional-audit-polish
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/features")
    public ResponseEntity<Map<String, Integer>> getCurrentFeatures(
            @RequestParam Long targetHotelId) {
        return ResponseEntity.ok(catalogService.getFeatures(targetHotelId));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/usage")
    public ResponseEntity<SubscriptionUsageDTO> getCurrentUsage(
            @RequestParam Long targetHotelId) {
        return ResponseEntity.ok(catalogService.getUsage(targetHotelId));
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

package com.hotel.controllers;

import com.hotel.dtos.AccountSubscriptionDTO;
import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
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

    private final SubscriptionCatalogService catalogService;

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        return ResponseEntity.ok(catalogService.getActivePlans());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<AccountSubscriptionDTO> getCurrentSubscription(
            @RequestParam Long targetHotelId) {
        return ResponseEntity.ok(catalogService.getCurrent(targetHotelId));
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
}

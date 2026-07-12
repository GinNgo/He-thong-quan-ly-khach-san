package com.hotel.controllers;

import com.hotel.entities.AccountSubscription;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.SubscriptionFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPlanRepository planRepository;
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final SubscriptionFeatureService featureService;

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        return ResponseEntity.ok(planRepository.findAll());
    }

    @GetMapping("/me")
    public ResponseEntity<List<AccountSubscription>> getMySubscriptions(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(accountSubscriptionRepository.findByUserIdAndStatus(userDetails.getUserId(), "ACTIVE"));
    }

    @GetMapping("/me/features")
    public ResponseEntity<Map<String, Integer>> getMyFeatures(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(featureService.getActiveFeaturesForUser(userDetails.getUserId()));
    }
}

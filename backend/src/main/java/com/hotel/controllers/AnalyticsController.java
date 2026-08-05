package com.hotel.controllers;

import com.hotel.dtos.AnalyticsDataDTO;
import com.hotel.services.AnalyticsService;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.hotel.security.CustomUserDetails;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    @Permission(function = FunctionCode.REPORT, action = ActionCode.VIEW)
    public ResponseEntity<AnalyticsDataDTO> getDashboardData(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(analyticsService.getAnalyticsData(userDetails));
    }
}

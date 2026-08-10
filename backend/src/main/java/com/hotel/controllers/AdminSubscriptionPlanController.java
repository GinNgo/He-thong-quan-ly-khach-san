package com.hotel.controllers;

import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.SubscriptionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/subscription-plans")
@RequiredArgsConstructor
public class AdminSubscriptionPlanController {
    private final SubscriptionCatalogService catalogService;

    @GetMapping
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.VIEW)
    public ResponseEntity<List<SubscriptionPlanDTO>> list() {
        return ResponseEntity.ok(catalogService.getAllPlansForAdministration());
    }
}

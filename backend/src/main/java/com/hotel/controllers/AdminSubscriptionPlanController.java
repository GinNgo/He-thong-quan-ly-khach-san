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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

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

    @PostMapping
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.CREATE)
    public ResponseEntity<SubscriptionPlanDTO> create(
            @RequestBody SubscriptionCatalogService.PlanCommand command) {
        return ResponseEntity.ok(catalogService.createPlan(command));
    }

    @PutMapping("/{id}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.UPDATE)
    public ResponseEntity<SubscriptionPlanDTO> update(
            @PathVariable Long id,
            @RequestBody SubscriptionCatalogService.PlanCommand command) {
        return ResponseEntity.ok(catalogService.updatePlan(id, command));
    }

    @PutMapping("/{id}/status")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.UPDATE)
    public ResponseEntity<SubscriptionPlanDTO> status(
            @PathVariable Long id,
            @RequestParam String value) {
        return ResponseEntity.ok(catalogService.setPlanStatus(id, value));
    }

    @DeleteMapping("/{id}")
    @Permission(function = FunctionCode.SYSTEM, action = ActionCode.DELETE)
    public ResponseEntity<SubscriptionPlanDTO> stopSelling(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.setPlanStatus(id, "INACTIVE"));
    }
}

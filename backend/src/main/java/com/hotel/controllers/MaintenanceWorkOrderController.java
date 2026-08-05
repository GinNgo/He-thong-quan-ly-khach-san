package com.hotel.controllers;

import com.hotel.dtos.MaintenanceWorkOrderDTO;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.MaintenanceWorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/maintenance-work-orders")
@RequiredArgsConstructor
public class MaintenanceWorkOrderController {
    private final MaintenanceWorkOrderService service;

    @GetMapping
    @Permission(function = FunctionCode.ROOM, action = ActionCode.VIEW)
    public ResponseEntity<List<MaintenanceWorkOrderDTO>> list(
            @RequestParam Long propertyId, @RequestParam(required = false) Long roomId) {
        return ResponseEntity.ok(service.list(propertyId, roomId));
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.VIEW)
    public ResponseEntity<MaintenanceWorkOrderDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @Permission(function = FunctionCode.ROOM, action = ActionCode.CREATE)
    public ResponseEntity<MaintenanceWorkOrderDTO> create(@Valid @RequestBody MaintenanceWorkOrderDTO request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PostMapping("/{id}/start")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<MaintenanceWorkOrderDTO> start(@PathVariable Long id) {
        return ResponseEntity.ok(service.start(id));
    }

    @PostMapping("/{id}/complete")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<MaintenanceWorkOrderDTO> complete(
            @PathVariable Long id, @RequestBody(required = false) MaintenanceWorkOrderDTO request) {
        return ResponseEntity.ok(service.complete(id, request));
    }

    @PostMapping("/{id}/reopen")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<MaintenanceWorkOrderDTO> reopen(
            @PathVariable Long id, @RequestBody MaintenanceWorkOrderDTO request) {
        return ResponseEntity.ok(service.reopen(id, request));
    }

    @PostMapping("/{id}/cancel")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.DELETE)
    public ResponseEntity<MaintenanceWorkOrderDTO> cancel(
            @PathVariable Long id, @RequestBody MaintenanceWorkOrderDTO request) {
        return ResponseEntity.ok(service.cancel(id, request));
    }
}

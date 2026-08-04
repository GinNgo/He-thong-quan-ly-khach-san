package com.hotel.controllers;

import com.hotel.dtos.HotelServiceDTO;
import com.hotel.services.HotelServiceLogic;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "Hotel Service", description = "Hotel Service Management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class HotelServiceController {

    private final HotelServiceLogic hotelServiceLogic;

    @GetMapping
    @Permission(function = FunctionCode.HOTEL_SERVICE, action = ActionCode.VIEW)
    @Operation(summary = "Get all services")
    public ResponseEntity<List<HotelServiceDTO>> getAllServices(
            @RequestParam(required = false) Long hotelId) {
        return ResponseEntity.ok(hotelServiceLogic.getAllServices(hotelId));
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.HOTEL_SERVICE, action = ActionCode.VIEW)
    @Operation(summary = "Get service by ID")
    public ResponseEntity<HotelServiceDTO> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(hotelServiceLogic.getServiceById(id));
    }

    @PostMapping
    @Permission(function = FunctionCode.HOTEL_SERVICE, action = ActionCode.CREATE)
    @Operation(summary = "Create new service")
    public ResponseEntity<HotelServiceDTO> createService(
            @RequestParam(required = false) Long hotelId,
            @Valid @RequestBody HotelServiceDTO dto) {
        return ResponseEntity.ok(hotelServiceLogic.createService(hotelId, dto));
    }

    @PutMapping("/{id}")
    @Permission(function = FunctionCode.HOTEL_SERVICE, action = ActionCode.UPDATE)
    @Operation(summary = "Update service")
    public ResponseEntity<HotelServiceDTO> updateService(@PathVariable Long id, @Valid @RequestBody HotelServiceDTO dto) {
        return ResponseEntity.ok(hotelServiceLogic.updateService(id, dto));
    }

    @DeleteMapping("/{id}")
    @Permission(function = FunctionCode.HOTEL_SERVICE, action = ActionCode.DELETE)
    @Operation(summary = "Delete service")
    public ResponseEntity<Void> deleteService(@PathVariable Long id, @RequestParam String reason) {
        hotelServiceLogic.deleteService(id, reason);
        return ResponseEntity.noContent().build();
    }
}

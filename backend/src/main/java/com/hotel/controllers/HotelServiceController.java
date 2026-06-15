package com.hotel.controllers;

import com.hotel.dtos.HotelServiceDTO;
import com.hotel.services.HotelServiceLogic;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "Hotel Service", description = "Hotel Service Management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class HotelServiceController {

    private final HotelServiceLogic hotelServiceLogic;

    @GetMapping
    @Operation(summary = "Get all services")
    public ResponseEntity<List<HotelServiceDTO>> getAllServices() {
        return ResponseEntity.ok(hotelServiceLogic.getAllServices());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get service by ID")
    public ResponseEntity<HotelServiceDTO> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(hotelServiceLogic.getServiceById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new service")
    public ResponseEntity<HotelServiceDTO> createService(@RequestBody HotelServiceDTO dto) {
        return ResponseEntity.ok(hotelServiceLogic.createService(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update service")
    public ResponseEntity<HotelServiceDTO> updateService(@PathVariable Long id, @RequestBody HotelServiceDTO dto) {
        return ResponseEntity.ok(hotelServiceLogic.updateService(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete service")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        hotelServiceLogic.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}

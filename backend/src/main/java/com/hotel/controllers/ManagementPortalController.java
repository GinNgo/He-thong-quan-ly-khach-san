package com.hotel.controllers;

import com.hotel.dtos.*;
import com.hotel.services.ManagementPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER','SUPER_ADMIN','ADMIN')")
public class ManagementPortalController {
    private final ManagementPortalService service;

    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> context(@RequestParam(required = false) Long activePropertyId) {
        return ResponseEntity.ok(service.context(activePropertyId));
    }

    @GetMapping("/properties")
    public ResponseEntity<List<Map<String, Object>>> properties() { return ResponseEntity.ok(service.properties()); }

    @GetMapping("/properties/{id}")
    public ResponseEntity<Map<String, Object>> property(@PathVariable Long id) {
        return ResponseEntity.ok(service.property(id));
    }

    @PostMapping("/properties")
    public ResponseEntity<Map<String, Object>> createProperty(@RequestBody ManagementPropertyRequest request) {
        return ResponseEntity.ok(service.createProperty(request));
    }

    @PutMapping("/properties/{id}")
    public ResponseEntity<Map<String, Object>> updateProperty(@PathVariable Long id,
                                                              @RequestBody ManagementPropertyRequest request) {
        return ResponseEntity.ok(service.updateProperty(id, request));
    }

    @GetMapping("/room-types")
    public ResponseEntity<List<RoomTypeDTO>> roomTypes(@RequestParam Long propertyId) {
        return ResponseEntity.ok(service.roomTypes(propertyId));
    }

    @PostMapping("/room-types")
    public ResponseEntity<RoomTypeDTO> createRoomType(@RequestBody RoomTypeDTO request) {
        return ResponseEntity.ok(service.createRoomType(request));
    }

    @PutMapping("/room-types/{id}")
    public ResponseEntity<RoomTypeDTO> updateRoomType(@PathVariable Long id, @RequestBody RoomTypeDTO request) {
        return ResponseEntity.ok(service.updateRoomType(id, request));
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomDTO>> rooms(@RequestParam Long propertyId) {
        return ResponseEntity.ok(service.rooms(propertyId));
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomDTO> createRoom(@RequestBody RoomDTO request) {
        return ResponseEntity.ok(service.createRoom(request));
    }

    @PostMapping("/rooms/bulk")
    public ResponseEntity<List<RoomDTO>> bulkRooms(@RequestBody BulkRoomRequest request) {
        return ResponseEntity.ok(service.bulkRooms(request));
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<RoomDTO> updateRoom(@PathVariable Long id, @RequestBody RoomDTO request) {
        return ResponseEntity.ok(service.updateRoom(id, request));
    }

    @PostMapping("/rooms/{id}/maintenance/start")
    public ResponseEntity<RoomDTO> startRoomMaintenance(@PathVariable Long id) {
        return ResponseEntity.ok(service.startRoomMaintenance(id));
    }

    @PostMapping("/rooms/{id}/maintenance/complete")
    public ResponseEntity<RoomDTO> completeRoomMaintenance(@PathVariable Long id) {
        return ResponseEntity.ok(service.completeRoomMaintenance(id));
    }

    @PostMapping("/housekeeping/{taskId}/complete")
    public ResponseEntity<Map<String, Object>> completeHousekeeping(@PathVariable Long taskId) {
        return ResponseEntity.ok(service.completeHousekeeping(taskId));
    }
}

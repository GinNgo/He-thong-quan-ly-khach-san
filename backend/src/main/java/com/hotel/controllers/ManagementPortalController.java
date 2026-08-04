package com.hotel.controllers;

import com.hotel.dtos.*;
import com.hotel.services.HotelManagementService;
import com.hotel.services.ManagementPortalService;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import jakarta.validation.Valid;
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
    private final HotelManagementService hotelManagementService;

    @GetMapping("/context")
    @Permission(function = FunctionCode.HOTEL, action = ActionCode.VIEW)
    public ResponseEntity<Map<String, Object>> context(@RequestParam(required = false) Long activePropertyId) {
        return ResponseEntity.ok(service.context(activePropertyId));
    }

    @GetMapping("/properties")
    @Permission(function = FunctionCode.HOTEL, action = ActionCode.VIEW)
    public ResponseEntity<List<PropertyProfileDTO>> properties() { return ResponseEntity.ok(service.properties()); }

    @GetMapping("/properties/{id}")
    @Permission(function = FunctionCode.HOTEL, action = ActionCode.VIEW)
    public ResponseEntity<PropertyProfileDTO> property(@PathVariable Long id) {
        return ResponseEntity.ok(hotelManagementService.getOwnedProfile(id));
    }

    @PostMapping("/properties")
    @Permission(function = FunctionCode.HOTEL, action = ActionCode.CREATE)
    public ResponseEntity<PropertyProfileDTO> createProperty(@Valid @RequestBody PropertyProfileDTO request) {
        return ResponseEntity.ok(service.createProperty(request));
    }

    @PutMapping("/properties/{id}")
    @Permission(function = FunctionCode.HOTEL, action = ActionCode.UPDATE)
    public ResponseEntity<PropertyProfileDTO> updateProperty(
            @PathVariable Long id,
            @Valid @RequestBody PropertyProfileUpdateRequest request) {
        return ResponseEntity.ok(hotelManagementService.updateOwnedHotel(id, request));
    }

    @GetMapping("/room-types")
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.VIEW)
    public ResponseEntity<List<RoomTypeDTO>> roomTypes(@RequestParam Long propertyId) {
        return ResponseEntity.ok(service.roomTypes(propertyId));
    }

    @PostMapping("/room-types")
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.CREATE)
    public ResponseEntity<RoomTypeDTO> createRoomType(@Valid @RequestBody RoomTypeDTO request) {
        return ResponseEntity.ok(service.createRoomType(request));
    }

    @PutMapping("/room-types/{id}")
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.UPDATE)
    public ResponseEntity<RoomTypeDTO> updateRoomType(@PathVariable Long id, @Valid @RequestBody RoomTypeDTO request) {
        return ResponseEntity.ok(service.updateRoomType(id, request));
    }

    @DeleteMapping("/room-types/{id}")
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.DELETE)
    public ResponseEntity<Void> deleteRoomType(@PathVariable Long id) {
        service.deleteRoomType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.VIEW)
    public ResponseEntity<List<RoomDTO>> rooms(@RequestParam Long propertyId) {
        return ResponseEntity.ok(service.rooms(propertyId));
    }

    @PostMapping("/rooms")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.CREATE)
    public ResponseEntity<RoomDTO> createRoom(@Valid @RequestBody RoomDTO request) {
        return ResponseEntity.ok(service.createRoom(request));
    }

    @PostMapping("/rooms/bulk")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.CREATE)
    public ResponseEntity<BulkRoomResultDTO> bulkRooms(@Valid @RequestBody BulkRoomRequest request) {
        return ResponseEntity.ok(service.bulkRooms(request));
    }

    @PutMapping("/rooms/{id}")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<RoomDTO> updateRoom(@PathVariable Long id, @Valid @RequestBody RoomDTO request) {
        return ResponseEntity.ok(service.updateRoom(id, request));
    }

    @PostMapping("/rooms/{id}/maintenance/start")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<RoomDTO> startRoomMaintenance(@PathVariable Long id) {
        return ResponseEntity.ok(service.startRoomMaintenance(id));
    }

    @PostMapping("/rooms/{id}/maintenance/complete")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<RoomDTO> completeRoomMaintenance(@PathVariable Long id) {
        return ResponseEntity.ok(service.completeRoomMaintenance(id));
    }

    @DeleteMapping("/rooms/{id}")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.DELETE)
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        service.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/housekeeping/{taskId}/complete")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<Map<String, Object>> completeHousekeeping(@PathVariable Long taskId) {
        return ResponseEntity.ok(service.completeHousekeeping(taskId));
    }
}

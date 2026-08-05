package com.hotel.controllers;

import com.hotel.dtos.RoomDTO;
import com.hotel.dtos.BulkRoomRequest;
import com.hotel.dtos.BulkRoomResultDTO;
import com.hotel.services.RoomService;
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
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Room", description = "Room Management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    @Permission(function = FunctionCode.ROOM, action = ActionCode.VIEW)
    @Operation(summary = "Get all rooms")
    public ResponseEntity<List<RoomDTO>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.VIEW)
    @Operation(summary = "Get room by ID")
    public ResponseEntity<RoomDTO> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @PostMapping
    @Permission(function = FunctionCode.ROOM, action = ActionCode.CREATE)
    @Operation(summary = "Create new room")
    public ResponseEntity<RoomDTO> createRoom(@Valid @RequestBody RoomDTO dto) {
        return ResponseEntity.ok(roomService.createRoom(dto));
    }

    @PostMapping("/bulk")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.CREATE)
    public ResponseEntity<BulkRoomResultDTO> bulkCreate(@Valid @RequestBody BulkRoomRequest request) {
        return ResponseEntity.ok(roomService.bulkCreate(request));
    }

    @PutMapping("/{id}")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    @Operation(summary = "Update room")
    public ResponseEntity<RoomDTO> updateRoom(@PathVariable Long id, @Valid @RequestBody RoomDTO dto) {
        return ResponseEntity.ok(roomService.updateRoom(id, dto));
    }

    @PostMapping("/{id}/maintenance/start")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    @Operation(summary = "Start room maintenance")
    public ResponseEntity<RoomDTO> startMaintenance(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.startMaintenance(id));
    }

    @PostMapping("/{id}/maintenance/complete")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    @Operation(summary = "Complete room maintenance")
    public ResponseEntity<RoomDTO> completeMaintenance(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.completeMaintenance(id));
    }

    @DeleteMapping("/{id}")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.DELETE)
    @Operation(summary = "Delete room")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
}

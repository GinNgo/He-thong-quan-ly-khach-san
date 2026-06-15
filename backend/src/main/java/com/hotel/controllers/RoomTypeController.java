package com.hotel.controllers;

import com.hotel.dtos.RoomTypeDTO;
import com.hotel.services.RoomTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/room-types")
@RequiredArgsConstructor
@Tag(name = "Room Type", description = "Room Type Management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    @GetMapping
    @Operation(summary = "Get all room types")
    public ResponseEntity<List<RoomTypeDTO>> getAllRoomTypes() {
        return ResponseEntity.ok(roomTypeService.getAllRoomTypes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get room type by ID")
    public ResponseEntity<RoomTypeDTO> getRoomTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(roomTypeService.getRoomTypeById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new room type")
    public ResponseEntity<RoomTypeDTO> createRoomType(@RequestBody RoomTypeDTO dto) {
        return ResponseEntity.ok(roomTypeService.createRoomType(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update room type")
    public ResponseEntity<RoomTypeDTO> updateRoomType(@PathVariable Long id, @RequestBody RoomTypeDTO dto) {
        return ResponseEntity.ok(roomTypeService.updateRoomType(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete room type")
    public ResponseEntity<Void> deleteRoomType(@PathVariable Long id) {
        roomTypeService.deleteRoomType(id);
        return ResponseEntity.noContent().build();
    }
}

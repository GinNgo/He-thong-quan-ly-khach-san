package com.hotel.controllers;

import com.hotel.dtos.RoomTypeDTO;
import com.hotel.services.RoomTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/room-types")
@RequiredArgsConstructor
@Tag(name = "Room Type", description = "Room Type Management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    @GetMapping
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.VIEW)
    @Operation(summary = "Get all room types")
    public ResponseEntity<List<RoomTypeDTO>> getAllRoomTypes() {
        return ResponseEntity.ok(roomTypeService.getAllRoomTypes());
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.VIEW)
    @Operation(summary = "Get room type by ID")
    public ResponseEntity<RoomTypeDTO> getRoomTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(roomTypeService.getRoomTypeById(id));
    }

    @PostMapping
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.CREATE)
    @Operation(summary = "Create new room type")
    public ResponseEntity<RoomTypeDTO> createRoomType(@RequestBody RoomTypeDTO dto) {
        return ResponseEntity.ok(roomTypeService.createRoomType(dto));
    }

    @PutMapping("/{id}")
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.UPDATE)
    @Operation(summary = "Update room type")
    public ResponseEntity<RoomTypeDTO> updateRoomType(@PathVariable Long id, @RequestBody RoomTypeDTO dto) {
        return ResponseEntity.ok(roomTypeService.updateRoomType(id, dto));
    }

    @DeleteMapping("/{id}")
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.DELETE)
    @Operation(summary = "Delete room type")
    public ResponseEntity<Void> deleteRoomType(@PathVariable Long id) {
        roomTypeService.deleteRoomType(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/hotel/{hotelId}")
    @Operation(summary = "Get all room types by hotel ID (Public)")
    public ResponseEntity<List<RoomTypeDTO>> getRoomTypesByHotelId(
            @PathVariable Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests) {
        return ResponseEntity.ok(roomTypeService.getRoomTypesByHotelId(hotelId, checkIn, checkOut, guests));
    }
}

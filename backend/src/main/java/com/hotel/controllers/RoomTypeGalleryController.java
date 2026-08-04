package com.hotel.controllers;

import com.hotel.dtos.RoomTypeGalleryImageDTO;
import com.hotel.dtos.RoomTypeGalleryOrderRequest;
import com.hotel.dtos.RoomTypeImageLinkRequest;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.RoomTypeGalleryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/room-types/{roomTypeId}/gallery")
@RequiredArgsConstructor
@Validated
public class RoomTypeGalleryController {
    private final RoomTypeGalleryService service;

    @GetMapping @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.VIEW)
    public ResponseEntity<List<RoomTypeGalleryImageDTO>> list(@PathVariable Long roomTypeId) { return ResponseEntity.ok(service.list(roomTypeId)); }

    @PostMapping("/links") @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.UPDATE)
    public ResponseEntity<RoomTypeGalleryImageDTO> addLink(@PathVariable Long roomTypeId, @Valid @RequestBody RoomTypeImageLinkRequest request) {
        return ResponseEntity.ok(service.addLink(roomTypeId, request));
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.UPDATE)
    public ResponseEntity<RoomTypeGalleryImageDTO> upload(@PathVariable Long roomTypeId, @RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank @Size(max = 255) String altTextVi,
            @RequestParam(required = false) @Size(max = 255) String altTextEn,
            @RequestParam(defaultValue = "false") boolean primary) {
        return ResponseEntity.ok(service.upload(roomTypeId, file, altTextVi, altTextEn, primary));
    }

    @PutMapping("/order") @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.UPDATE)
    public ResponseEntity<List<RoomTypeGalleryImageDTO>> reorder(@PathVariable Long roomTypeId, @Valid @RequestBody RoomTypeGalleryOrderRequest request) {
        return ResponseEntity.ok(service.reorder(roomTypeId, request));
    }

    @PutMapping("/images/{imageId}/primary") @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.UPDATE)
    public ResponseEntity<RoomTypeGalleryImageDTO> primary(@PathVariable Long roomTypeId, @PathVariable Long imageId) {
        return ResponseEntity.ok(service.setPrimary(roomTypeId, imageId));
    }

    @DeleteMapping("/images/{imageId}") @Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.UPDATE)
    public ResponseEntity<List<RoomTypeGalleryImageDTO>> delete(@PathVariable Long roomTypeId, @PathVariable Long imageId) {
        return ResponseEntity.ok(service.delete(roomTypeId, imageId));
    }
}

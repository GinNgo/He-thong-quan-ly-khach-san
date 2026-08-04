package com.hotel.controllers;

import com.hotel.dtos.RoomGalleryImageDTO;
import com.hotel.dtos.RoomGalleryOrderRequest;
import com.hotel.dtos.RoomImageLinkRequest;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.RoomGalleryService;
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
@RequestMapping("/api/v1/rooms/{roomId}/gallery")
@RequiredArgsConstructor
@Validated
public class RoomGalleryController {
    private final RoomGalleryService service;

    @GetMapping
    @Permission(function = FunctionCode.ROOM, action = ActionCode.VIEW)
    public ResponseEntity<List<RoomGalleryImageDTO>> list(@PathVariable Long roomId) {
        return ResponseEntity.ok(service.list(roomId));
    }

    @PostMapping("/links")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<RoomGalleryImageDTO> addLink(
            @PathVariable Long roomId, @Valid @RequestBody RoomImageLinkRequest request) {
        return ResponseEntity.ok(service.addLink(roomId, request));
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<RoomGalleryImageDTO> upload(
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank @Size(max = 255) String altTextVi,
            @RequestParam(required = false) @Size(max = 255) String altTextEn,
            @RequestParam(defaultValue = "false") boolean primary) {
        return ResponseEntity.ok(service.upload(roomId, file, altTextVi, altTextEn, primary));
    }

    @PutMapping("/order")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<List<RoomGalleryImageDTO>> reorder(
            @PathVariable Long roomId, @Valid @RequestBody RoomGalleryOrderRequest request) {
        return ResponseEntity.ok(service.reorder(roomId, request));
    }

    @PutMapping("/images/{imageId}/primary")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<RoomGalleryImageDTO> primary(@PathVariable Long roomId, @PathVariable Long imageId) {
        return ResponseEntity.ok(service.setPrimary(roomId, imageId));
    }

    @DeleteMapping("/images/{imageId}")
    @Permission(function = FunctionCode.ROOM, action = ActionCode.UPDATE)
    public ResponseEntity<List<RoomGalleryImageDTO>> delete(@PathVariable Long roomId, @PathVariable Long imageId) {
        return ResponseEntity.ok(service.delete(roomId, imageId));
    }
}

package com.hotel.controllers;

import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.dtos.PropertyCreateRequest;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyUpdateRequest;
import com.hotel.dtos.PublicHotelDetailDTO;
import com.hotel.entities.Hotel;
import com.hotel.services.HotelManagementService;
import com.hotel.services.PropertyRegistrationService;
import com.hotel.services.RoomTypeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/hotels")
@Validated
public class HotelController {

    private final HotelManagementService hotelService;
    private final RoomTypeService roomTypeService;
    private final PropertyRegistrationService propertyRegistrationService;

    public HotelController(HotelManagementService hotelService, RoomTypeService roomTypeService,
                           PropertyRegistrationService propertyRegistrationService) {
        this.hotelService = hotelService;
        this.roomTypeService = roomTypeService;
        this.propertyRegistrationService = propertyRegistrationService;
    }

    @GetMapping("/public/search")
    public ResponseEntity<List<Hotel>> searchHotels(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests) {
        List<Hotel> hotels = hotelService.searchHotels(city, "ACTIVE");
        if (provinceId != null) {
            hotels = hotels.stream().filter(hotel -> provinceId.equals(hotel.getProvinceId())).toList();
        }
        if (wardId != null) {
            hotels = hotels.stream().filter(hotel -> wardId.equals(hotel.getWardId())).toList();
        }
        if (checkIn != null || checkOut != null || guests != null) {
            hotels = hotels.stream()
                    .filter(hotel -> !roomTypeService.getRoomTypesByHotelId(
                            hotel.getId(), checkIn, checkOut, guests).isEmpty())
                    .toList();
        }
        return ResponseEntity.ok(hotels);
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<PublicHotelDetailDTO> getHotelById(@PathVariable Long id) {
        return hotelService.getHotelById(id)
                .filter(hotel -> "ACTIVE".equalsIgnoreCase(hotel.getStatus()))
                .filter(hotel -> "APPROVED".equalsIgnoreCase(hotel.getApprovalStatus()))
                .filter(hotel -> "ACTIVE".equalsIgnoreCase(hotel.getOperationStatus()))
                .map(PublicHotelDetailDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my-hotels")
    public ResponseEntity<List<Hotel>> getMyHotels(
            @AuthenticationPrincipal com.hotel.security.CustomUserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(hotelService.getHotelsByOwnerId(userDetails.getUserId()));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<PropertyProfileDTO>> getAllHotels() {
        return ResponseEntity.ok(hotelService.getAllHotels().stream().map(PropertyProfileDTO::from).toList());
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<PropertyProfileDTO> createHotel(@Valid @RequestBody PropertyCreateRequest request) {
        return ResponseEntity.ok(hotelService.createHotel(request));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<PropertyProfileDTO> updateHotel(
            @PathVariable Long id,
            @Valid @RequestBody PropertyUpdateRequest request) {
        return ResponseEntity.ok(hotelService.updateHotel(id, request));
    }

    /** Legacy DELETE remains compatible, but now closes and retains the property aggregate. */
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<PropertyProfileDTO> deleteHotel(
            @PathVariable Long id,
            @RequestParam @NotBlank @Size(min = 5, max = 500) String reason) {
        return ResponseEntity.ok(hotelService.closeHotel(id, new PropertyClosureRequest(reason)));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping("/{id}/close")
    public ResponseEntity<PropertyProfileDTO> closeHotel(
            @PathVariable Long id,
            @Valid @RequestBody PropertyClosureRequest request) {
        return ResponseEntity.ok(hotelService.closeHotel(id, request));
    }

    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER', 'SUPER_ADMIN')")
    @PostMapping("/{id}/submit")
    public ResponseEntity<PropertyProfileDTO> submitHotel(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.submitHotel(id));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<Hotel> approveHotel(@PathVariable Long id) {
        return ResponseEntity.ok(propertyRegistrationService.approveProperty(id));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Hotel> rejectHotel(@PathVariable Long id) {
        return ResponseEntity.ok(propertyRegistrationService.rejectProperty(id));
    }
}

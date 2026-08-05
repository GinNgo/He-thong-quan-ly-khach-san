package com.hotel.controllers;

import com.hotel.entities.Hotel;
import com.hotel.dtos.PropertyApprovalDecisionResponse;
import com.hotel.dtos.PropertyApprovalRejectionRequest;
import com.hotel.dtos.PropertyApprovalSubmissionResponse;
import com.hotel.dtos.PublicHotelDetailDTO;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.HotelManagementService;
import com.hotel.services.PropertyApprovalWorkflowService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.services.RoomTypeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    @Autowired
    private HotelManagementService hotelService;

    @Autowired
    private RoomTypeService roomTypeService;

    @Autowired
    private PropertyApprovalWorkflowService propertyApprovalWorkflowService;

    @Autowired
    private PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;

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
            hotels = hotels.stream().filter(h -> provinceId.equals(h.getProvinceId())).toList();
        }
        if (districtId != null) {
            // District is mapped implicitly or no longer used directly in Hotel entity
        }
        if (wardId != null) {
            hotels = hotels.stream().filter(h -> wardId.equals(h.getWardId())).toList();
        }

        if (checkIn != null || checkOut != null || guests != null) {
            hotels = hotels.stream()
                    .filter(hotel -> !roomTypeService.getRoomTypesByHotelId(hotel.getId(), checkIn, checkOut, guests).isEmpty())
                    .toList();
        }

        return ResponseEntity.ok(hotels.stream()
                .filter(publicInventoryEligibilityPolicy::isPublicProperty)
                .toList());
    }
    @GetMapping("/public/{id}")
    public ResponseEntity<PublicHotelDetailDTO> getHotelById(@PathVariable Long id) {
        return ResponseEntity.ok(PublicHotelDetailDTO.from(
                publicInventoryEligibilityPolicy.requirePublicProperty(id)));
    }

    @GetMapping("/my-hotels")
    public ResponseEntity<List<Hotel>> getMyHotels(@org.springframework.security.core.annotation.AuthenticationPrincipal com.hotel.security.CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(hotelService.getHotelsByOwnerId(userDetails.getUserId()));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<PublicHotelDetailDTO>> getAllHotels() {
        return ResponseEntity.ok(hotelService.getAllHotels().stream().map(PublicHotelDetailDTO::from).toList());
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<Hotel> createHotel(@RequestBody Hotel hotel) {
        return ResponseEntity.ok(hotelService.createHotel(hotel));
    }



    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Hotel> updateHotel(@PathVariable Long id, @RequestBody Hotel hotel) {
        return ResponseEntity.ok(hotelService.updateHotel(id, hotel));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHotel(@PathVariable Long id) {
        hotelService.deleteHotel(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/submit")
    public ResponseEntity<PropertyApprovalSubmissionResponse> submitHotel(
            @PathVariable Long id,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("Authoritative authenticated account context is required.");
        }
        return ResponseEntity.ok(propertyApprovalWorkflowService.submitDraft(userDetails.getUserId(), id));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<PropertyApprovalDecisionResponse> approveHotel(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(propertyApprovalWorkflowService.approve(
                requireAuthoritativePrincipal(authentication).getUserId(), id));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<PropertyApprovalDecisionResponse> rejectHotel(
            @PathVariable Long id,
            @Valid @RequestBody PropertyApprovalRejectionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(propertyApprovalWorkflowService.reject(
                requireAuthoritativePrincipal(authentication).getUserId(), id, request.reason()));
    }

    private CustomUserDetails requireAuthoritativePrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("Authoritative authenticated account context is required.");
        }
        return userDetails;
    }
}

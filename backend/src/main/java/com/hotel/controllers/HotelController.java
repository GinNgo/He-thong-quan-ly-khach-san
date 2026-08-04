package com.hotel.controllers;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.dtos.PublicHotelDetailDTO;
import com.hotel.entities.Hotel;
import com.hotel.services.HotelManagementService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.services.PropertySearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    @Autowired
    private HotelManagementService hotelService;

    @Autowired
    private PropertySearchService propertySearchService;

    @Autowired
    private PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;

    @Autowired
    private com.hotel.services.PropertyAccessService propertyAccessService;

    @Autowired
    private com.hotel.services.PropertyRegistrationService propertyRegistrationService;

    @GetMapping("/public/search")
    public ResponseEntity<Page<PropertySearchResponseDTO>> searchHotels(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {

        if (districtId != null) {
            throw new IllegalArgumentException("districtId is no longer supported; use provinceId and wardId.");
        }
        if (guests != null && guests <= 0) {
            throw new IllegalArgumentException("guests must be greater than zero.");
        }

        PropertySearchRequestDTO request = new PropertySearchRequestDTO();
        request.setLegacyAddressKeyword(city);
        request.setProvinceId(provinceId);
        request.setWardId(wardId);
        request.setCheckInDate(checkIn == null ? null : checkIn.toString());
        request.setCheckOutDate(checkOut == null ? null : checkOut.toString());
        request.setAdultCount(guests);
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-LuxeStay-Freshness", "LIVE_SEARCH")
                .body(propertySearchService.searchProperties(request));
    }
    @GetMapping("/public/{id}")
    public ResponseEntity<PublicHotelDetailDTO> getHotelById(@PathVariable Long id) {
        Hotel hotel = publicInventoryEligibilityPolicy.requirePublicProperty(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PublicHotelDetailDTO.from(hotel));
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

    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER', 'SUPER_ADMIN')")
    @PostMapping("/{id}/submit")
    public ResponseEntity<Hotel> submitHotel(@PathVariable Long id) {
        Hotel hotel = hotelService.getHotelById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy cơ sở."));
        propertyAccessService.requireAssignedHotel(id);
        hotel.setStatus("PENDING");
        hotel.setApprovalStatus("PENDING_APPROVAL");
        hotel.setOperationStatus("INACTIVE");
        return ResponseEntity.ok(hotelService.updateHotel(id, hotel));
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

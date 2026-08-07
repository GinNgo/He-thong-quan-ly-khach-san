package com.hotel.controllers;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.dtos.PropertyApprovalDecisionResponse;
import com.hotel.dtos.PropertyApprovalRejectionRequest;
import com.hotel.dtos.PropertyApprovalSubmissionResponse;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyProfileUpdateRequest;
import com.hotel.dtos.PublicHotelDetailDTO;
import com.hotel.entities.Hotel;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.HotelManagementService;
import com.hotel.services.PropertyApprovalWorkflowService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.services.PropertySearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
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
    private final PropertySearchService propertySearchService;
    private final PropertyApprovalWorkflowService propertyApprovalWorkflowService;
    private final PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;

    public HotelController(HotelManagementService hotelService, PropertySearchService propertySearchService,
                           PropertyApprovalWorkflowService propertyApprovalWorkflowService,
                           PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy) {
        this.hotelService = hotelService;
        this.propertySearchService = propertySearchService;
        this.propertyApprovalWorkflowService = propertyApprovalWorkflowService;
        this.publicInventoryEligibilityPolicy = publicInventoryEligibilityPolicy;
    }

    @Autowired
    private com.hotel.services.PublicPlacementDisclosureService publicPlacementDisclosureService;

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
        if (guests != null && guests <= 0) throw new IllegalArgumentException("guests must be greater than zero.");

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
<<<<<<< HEAD
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PublicHotelDetailDTO.from(publicInventoryEligibilityPolicy.requirePublicProperty(id)));
=======
        return hotelService.getHotelById(id)
                .map(hotel -> {
                    PublicHotelDetailDTO dto = PublicHotelDetailDTO.from(hotel);
                    dto.setSponsoredPlacement(publicPlacementDisclosureService.searchDisclosure(hotel.getId()).orElse(null));
                    return dto;
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
>>>>>>> codex/ui-functional-audit-polish
    }

    @GetMapping("/my-hotels")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PropertyProfileDTO>> getMyHotels(
            @AuthenticationPrincipal com.hotel.security.CustomUserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(hotelService.getHotelsByOwnerId(userDetails.getUserId()).stream()
                .map(PropertyProfileDTO::from).toList());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/accessible")
    public ResponseEntity<List<PublicHotelDetailDTO>> getAccessibleHotels() {
        return ResponseEntity.ok(hotelService.getAccessibleHotels().stream()
                .map(PublicHotelDetailDTO::from)
                .toList());
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<PropertyProfileDTO>> getAllHotels() {
        return ResponseEntity.ok(hotelService.getAllHotels().stream().map(PropertyProfileDTO::from).toList());
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<PropertyProfileDTO> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getProfile(id));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<PropertyProfileDTO> createHotel(@Valid @RequestBody PropertyProfileDTO request) {
        return ResponseEntity.ok(hotelService.createHotel(request));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<PropertyProfileDTO> updateHotel(
            @PathVariable Long id,
            @Valid @RequestBody PropertyProfileUpdateRequest request) {
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

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/submit")
    public ResponseEntity<PropertyApprovalSubmissionResponse> submitHotel(
            @PathVariable Long id,
            Authentication authentication) {
        CustomUserDetails principal = requireAuthoritativePrincipal(authentication);
        return ResponseEntity.ok(propertyApprovalWorkflowService.submitDraft(principal.getUserId(), id));
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

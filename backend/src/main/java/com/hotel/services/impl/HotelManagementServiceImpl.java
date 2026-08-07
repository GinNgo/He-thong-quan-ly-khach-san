package com.hotel.services.impl;

import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyProfileUpdateRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.services.HotelManagementService;
import com.hotel.services.OperationalAuditService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PropertyProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HotelManagementServiceImpl implements HotelManagementService {

    private static final Set<String> OWNER_EDITABLE_APPROVAL_STATES = Set.of("DRAFT", "REJECTED", "APPROVED");

    private final HotelRepository hotelRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final PropertyAccessService propertyAccessService;
    private final PropertyProfileMapper propertyProfileMapper;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

    @Override
    public List<Hotel> getAllHotels() {
        return hotelRepository.findAll();
    }

    @Override
    public List<Hotel> searchHotels(String city, String status) {
        if (city == null || city.trim().isEmpty()) {
            return hotelRepository.findByStatus(status);
        }
        return hotelRepository.findByAddressLineContainingIgnoreCaseAndStatus(city, status);
    }

    @Override
    public Optional<Hotel> getHotelById(Long id) {
        return hotelRepository.findById(id);
    }

    @Override
    @Transactional
    public PropertyProfileDTO createHotel(PropertyProfileDTO request) {
        requireSystemAdministrator();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Hotel hotel = new Hotel();
        hotel.setCode("ADMIN-" + suffix.toUpperCase(Locale.ROOT));
        hotel.setSlug("admin-property-" + suffix);
        propertyProfileMapper.apply(hotel, request);
        hotel.setStatus("DRAFT");
        hotel.setApprovalStatus("DRAFT");
        hotel.setOperationStatus("INACTIVE");
        hotel.setIsDemo(false);
        hotel.setDataSource("ADMIN");
        Hotel saved = hotelRepository.saveAndFlush(hotel);
        audit(saved, "PROPERTY_CREATED", null, snapshot(saved), "Administrative property draft created");
        return PropertyProfileDTO.from(saved);
    }

    @Override
    @Transactional
    public PropertyProfileDTO updateHotel(Long id, PropertyProfileUpdateRequest request) {
        requireSystemAdministrator();
        return updateLocked(id, request, false);
    }

    @Override
    @Transactional
    public PropertyProfileDTO updateOwnedHotel(Long id, PropertyProfileUpdateRequest request) {
        requireActiveOwner(id);
        return updateLocked(id, request, true);
    }

    private PropertyProfileDTO updateLocked(Long id, PropertyProfileUpdateRequest request, boolean ownerMutation) {
        if (request == null || request.profile() == null) {
            throw new IllegalArgumentException("Property profile update is required.");
        }
        Hotel hotel = requireLocked(id);
        requireEditableState(hotel, ownerMutation);
        Map<String, Object> before = snapshot(hotel);
        propertyProfileMapper.apply(hotel, request.profile());
        Hotel saved = hotelRepository.saveAndFlush(hotel);
        audit(saved, ownerMutation ? "PROPERTY_OWNER_UPDATED" : "PROPERTY_ADMIN_UPDATED",
                before, snapshot(saved), request.reason().trim());
        return PropertyProfileDTO.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyProfileDTO getProfile(Long id) {
        requireSystemAdministrator();
        return PropertyProfileDTO.from(hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found.")));
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyProfileDTO getOwnedProfile(Long id) {
        requireActiveOwner(id);
        return PropertyProfileDTO.from(hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found.")));
    }

    @Override
    @Transactional
    public PropertyProfileDTO submitHotel(Long id) {
        if (!propertyAccessService.isSystemAdministrator()) {
            requireActiveOwner(id);
        }
        Hotel hotel = requireLocked(id);
        String approval = normalize(hotel.getApprovalStatus());
        if (!Set.of("DRAFT", "REJECTED").contains(approval)) {
            throw new IllegalStateException("Only draft or rejected properties can be submitted for approval.");
        }
        Map<String, Object> before = snapshot(hotel);
        hotel.setStatus("PENDING");
        hotel.setApprovalStatus("PENDING_APPROVAL");
        hotel.setOperationStatus("INACTIVE");
        Hotel saved = hotelRepository.saveAndFlush(hotel);
        audit(saved, "PROPERTY_SUBMITTED", before, snapshot(saved), "Property submitted for approval");
        return PropertyProfileDTO.from(saved);
    }

    @Override
    @Transactional
    public PropertyProfileDTO closeHotel(Long id, PropertyClosureRequest request) {
        requireSystemAdministrator();
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("Property closure reason is required.");
        }
        Hotel hotel = requireLocked(id);
        if ("CLOSED".equals(normalize(hotel.getOperationStatus()))) {
            return PropertyProfileDTO.from(hotel);
        }
        Map<String, Object> before = snapshot(hotel);
        hotel.setStatus("CLOSED");
        hotel.setOperationStatus("CLOSED");
        Hotel saved = hotelRepository.saveAndFlush(hotel);
        audit(saved, "PROPERTY_CLOSED", before, snapshot(saved), request.reason().trim());
        return PropertyProfileDTO.from(saved);
    }

    @Override
    public List<Hotel> getHotelsByOwnerId(Long ownerId) {
        return hotelRepository.findByOwnerId(ownerId);
    }

<<<<<<< HEAD
    private void requireEditableState(Hotel hotel, boolean ownerMutation) {
        String approval = normalize(hotel.getApprovalStatus());
        String operation = normalize(hotel.getOperationStatus());
        if ("CLOSED".equals(operation) || "CLOSED".equals(normalize(hotel.getStatus()))) {
            throw new IllegalStateException("Closed properties are retained as read-only records.");
        }
        if ("PENDING_APPROVAL".equals(approval)) {
            throw new IllegalStateException("A property under review cannot be edited.");
        }
        if (ownerMutation && (!OWNER_EDITABLE_APPROVAL_STATES.contains(approval)
                || ("APPROVED".equals(approval) && !"ACTIVE".equals(operation)))) {
            throw new IllegalStateException("The property lifecycle does not allow owner profile edits.");
        }
    }

    private void requireActiveOwner(Long hotelId) {
        User user = propertyAccessService.currentUser();
        UserProperty ownership = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(user.getId(), hotelId, "OWNER")
                .filter(item -> "ACTIVE".equals(normalize(item.getStatus())))
                .orElseThrow(() -> new ResourceNotFoundException("Property not found."));
        if (ownership.getHotel() == null || !hotelId.equals(ownership.getHotel().getId())) {
            throw new ResourceNotFoundException("Property not found.");
        }
    }

    private Hotel requireLocked(Long id) {
        return hotelRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found."));
    }

    private void requireSystemAdministrator() {
        if (!propertyAccessService.isSystemAdministrator()) {
            throw new SecurityException("System administrator access is required.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> snapshot(Hotel hotel) {
        if (hotel == null) return null;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", hotel.getId());
        state.put("nameVi", hotel.getNameVi());
        state.put("propertyType", hotel.getPropertyType());
        state.put("addressLine", hotel.getAddressLine());
        state.put("provinceId", hotel.getProvinceId());
        state.put("wardId", hotel.getWardId());
        state.put("latitude", hotel.getLatitude());
        state.put("longitude", hotel.getLongitude());
        state.put("descriptionVi", hotel.getDescriptionVi());
        state.put("descriptionEn", hotel.getDescriptionEn());
        state.put("starRating", hotel.getStarRating());
        state.put("phone", hotel.getPhone());
        state.put("email", hotel.getEmail());
        state.put("website", hotel.getWebsite());
        state.put("checkinTime", hotel.getCheckinTime());
        state.put("checkoutTime", hotel.getCheckoutTime());
        state.put("minPrice", hotel.getMinPrice());
        state.put("maxPrice", hotel.getMaxPrice());
        state.put("mainImage", hotel.getMainImage());
        state.put("status", hotel.getStatus());
        state.put("approvalStatus", hotel.getApprovalStatus());
        state.put("operationStatus", hotel.getOperationStatus());
        return state;
    }

    private void audit(Hotel hotel, String eventType, Object before, Object after, String reason) {
        if (operationalAuditService == null || hotel == null) return;
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT", hotel.getId(), "PROPERTY", eventType, "HOTEL", String.valueOf(hotel.getId()),
                null, null, reason, before, after, null));
    }

=======
    @Override
    public List<Hotel> getAccessibleHotels() {
        if (propertyAccessService.isSystemAdministrator()) {
            return hotelRepository.findAll();
        }
        return hotelRepository.findAllById(propertyAccessService.assignedHotelIds());
    }
>>>>>>> codex/ui-functional-audit-polish
}

package com.hotel.services.impl;

import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.dtos.PropertyCreateRequest;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyUpdateRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.services.HotelManagementService;
import com.hotel.services.OperationalAuditService;
import com.hotel.services.PropertyAccessService;
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

    private static final Set<String> PROPERTY_TYPES = Set.of("HOTEL", "MOTEL", "HOMESTAY", "APARTMENT", "VILLA", "RESORT");
    private static final Set<String> OWNER_EDITABLE_APPROVAL_STATES = Set.of("DRAFT", "REJECTED", "APPROVED");

    private final HotelRepository hotelRepository;
    private final LocationRepository locationRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final PropertyAccessService propertyAccessService;

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
    public PropertyProfileDTO createHotel(PropertyCreateRequest request) {
        requireSystemAdministrator();
        LocationPair location = validateLocation(request.getProvinceId(), request.getWardId());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Hotel hotel = new Hotel();
        hotel.setCode("ADMIN-" + suffix.toUpperCase(Locale.ROOT));
        hotel.setSlug("admin-property-" + suffix);
        applyCreateProfile(hotel, request, location);
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
    public PropertyProfileDTO updateHotel(Long id, PropertyUpdateRequest request) {
        requireSystemAdministrator();
        return updateLocked(id, request, false);
    }

    @Override
    @Transactional
    public PropertyProfileDTO updateOwnedHotel(Long id, PropertyUpdateRequest request) {
        requireActiveOwner(id);
        return updateLocked(id, request, true);
    }

    private PropertyProfileDTO updateLocked(Long id, PropertyUpdateRequest request, boolean ownerMutation) {
        requireChanges(request);
        Hotel hotel = requireLocked(id);
        requireEditableState(hotel, ownerMutation);
        Map<String, Object> before = snapshot(hotel);
        applyUpdateProfile(hotel, request);
        Hotel saved = hotelRepository.saveAndFlush(hotel);
        audit(saved, ownerMutation ? "PROPERTY_OWNER_UPDATED" : "PROPERTY_ADMIN_UPDATED",
                before, snapshot(saved), request.getReason().trim());
        return PropertyProfileDTO.from(saved);
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

    private void applyCreateProfile(Hotel hotel, PropertyCreateRequest request, LocationPair location) {
        hotel.setName(requireText(request.getNameVi(), "Property name is required."));
        hotel.setNameVi(request.getNameVi().trim());
        hotel.setNameEn(trimToNull(request.getNameEn()));
        hotel.setPropertyType(requirePropertyType(request.getPropertyType()));
        hotel.setAddressLine(requireText(request.getAddressLine(), "Property address is required."));
        hotel.setProvinceId(location.province().getId());
        hotel.setWardId(location.ward().getId());
        hotel.setCity(location.province().getNameVi());
        hotel.setCountry("Vietnam");
        hotel.setDescription(trimToNull(request.getDescriptionVi()));
        hotel.setDescriptionVi(trimToNull(request.getDescriptionVi()));
        hotel.setDescriptionEn(trimToNull(request.getDescriptionEn()));
        hotel.setStarRating(request.getStarRating());
        hotel.setPhone(trimToNull(request.getPhone()));
        hotel.setEmail(trimToNull(request.getEmail()));
        hotel.setWebsite(trimToNull(request.getWebsite()));
        hotel.setMainImage(trimToNull(request.getMainImage()));
    }

    private void applyUpdateProfile(Hotel hotel, PropertyUpdateRequest request) {
        if (request.getNameVi() != null) {
            hotel.setName(requireText(request.getNameVi(), "Property name cannot be blank."));
            hotel.setNameVi(request.getNameVi().trim());
        }
        if (request.getNameEn() != null) hotel.setNameEn(trimToNull(request.getNameEn()));
        if (request.getPropertyType() != null) hotel.setPropertyType(requirePropertyType(request.getPropertyType()));
        if (request.getAddressLine() != null) hotel.setAddressLine(requireText(request.getAddressLine(), "Property address cannot be blank."));
        if (request.getProvinceId() != null || request.getWardId() != null) {
            Long provinceId = request.getProvinceId() == null ? hotel.getProvinceId() : request.getProvinceId();
            Long wardId = request.getWardId() == null ? hotel.getWardId() : request.getWardId();
            LocationPair location = validateLocation(provinceId, wardId);
            hotel.setProvinceId(location.province().getId());
            hotel.setWardId(location.ward().getId());
            hotel.setCity(location.province().getNameVi());
        }
        if (request.getDescriptionVi() != null) {
            hotel.setDescription(trimToNull(request.getDescriptionVi()));
            hotel.setDescriptionVi(trimToNull(request.getDescriptionVi()));
        }
        if (request.getDescriptionEn() != null) hotel.setDescriptionEn(trimToNull(request.getDescriptionEn()));
        if (request.getStarRating() != null) hotel.setStarRating(request.getStarRating());
        if (request.getPhone() != null) hotel.setPhone(trimToNull(request.getPhone()));
        if (request.getEmail() != null) hotel.setEmail(trimToNull(request.getEmail()));
        if (request.getWebsite() != null) hotel.setWebsite(trimToNull(request.getWebsite()));
        if (request.getMainImage() != null) hotel.setMainImage(trimToNull(request.getMainImage()));
    }

    private LocationPair validateLocation(Long provinceId, Long wardId) {
        Location province = locationRepository.findById(provinceId)
                .filter(item -> "PROVINCE".equals(normalize(item.getLocationType())))
                .orElseThrow(() -> new IllegalArgumentException("Province is invalid."));
        Location ward = locationRepository.findById(wardId)
                .filter(item -> "WARD".equals(normalize(item.getLocationType())))
                .orElseThrow(() -> new IllegalArgumentException("Ward is invalid."));
        if (ward.getParent() == null || !province.getId().equals(ward.getParent().getId())) {
            throw new IllegalArgumentException("Ward does not belong to the selected province.");
        }
        return new LocationPair(province, ward);
    }

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

    private void requireChanges(PropertyUpdateRequest request) {
        if (request == null) throw new IllegalArgumentException("Property update is required.");
        boolean changed = request.getNameVi() != null || request.getNameEn() != null
                || request.getPropertyType() != null || request.getAddressLine() != null
                || request.getProvinceId() != null || request.getWardId() != null
                || request.getDescriptionVi() != null || request.getDescriptionEn() != null
                || request.getStarRating() != null || request.getPhone() != null
                || request.getEmail() != null || request.getWebsite() != null || request.getMainImage() != null;
        if (!changed) throw new IllegalArgumentException("At least one editable property field is required.");
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

    private String requirePropertyType(String value) {
        String normalized = normalize(value);
        if (!PROPERTY_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Property type is invalid.");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private record LocationPair(Location province, Location ward) {
    }
}

package com.hotel.services;

import com.hotel.dtos.PartnerRegistrationRequest;
import com.hotel.dtos.PartnerRegistrationResponse;
import com.hotel.dtos.PartnerConversionRequest;
import com.hotel.entities.*;
import com.hotel.exceptions.RegistrationConflictException;
import com.hotel.repositories.*;
import com.hotel.util.VietnameseTextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyRegistrationService {

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final LocationRepository locationRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final PropertyOwnershipLifecycleService ownershipLifecycleService;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

    @Transactional
    public PartnerRegistrationResponse registerAnonymousPartner(PartnerRegistrationRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByUsernameIgnoreCase(email)) {
            throw RegistrationConflictException.email();
        }
        requireValidLocationSelection(request.getProvinceId(), request.getWardId());

        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName(normalizeDisplayText(request.getFullName()));
        user.setPhone(request.getPhone().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicateIdentity) {
            throw RegistrationConflictException.email();
        }

        Hotel property = createDraftProperty(user, request.getPropertyName(), request.getProvinceId(),
                request.getWardId(), request.getAddress());
        ownershipLifecycleService.createPendingOwner(user, property);

        return new PartnerRegistrationResponse(user.getId(), property.getId(), "DRAFT");
    }

    @Transactional
    public PartnerRegistrationResponse convertExistingCustomer(
            Long authenticatedUserId,
            PartnerConversionRequest request) {
        if (authenticatedUserId == null) {
            throw new AccessDeniedException("Authenticated account id is required.");
        }
        User user = userRepository.findByIdForUpdate(authenticatedUserId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated account is unavailable."));
        com.hotel.security.AccountStatusPolicy.requireActive(user);

        String canonicalEmail = normalizeEmail(user.getEmail());
        User emailOwner = userRepository.findByEmailIgnoreCase(canonicalEmail)
                .orElseThrow(() -> new AccessDeniedException("Authenticated account identity is inconsistent."));
        if (!authenticatedUserId.equals(emailOwner.getId())) {
            throw new AccessDeniedException("Authenticated account identity does not match the email owner.");
        }

        Hotel property = createDraftProperty(user, request.getPropertyName(), request.getProvinceId(),
                request.getWardId(), request.getAddress());
        ownershipLifecycleService.createPendingOwner(user, property);
        return new PartnerRegistrationResponse(user.getId(), property.getId(), "DRAFT");
    }

    private Hotel createDraftProperty(
            User user,
            String requestedPropertyName,
            Long provinceId,
            Long wardId,
            String requestedAddress) {
        LocationSelection locations = requireValidLocationSelection(provinceId, wardId);
        Location province = locations.province();
        Location ward = locations.ward();

        String propertyName = normalizeDisplayText(requestedPropertyName);
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Hotel property = new Hotel();
        property.setName(propertyName);
        property.setNameVi(propertyName);
        property.setCode("PARTNER-" + uniqueSuffix.toUpperCase(Locale.ROOT));
        String normalizedSlug = VietnameseTextNormalizer.normalize(propertyName);
        String slugBase = normalizedSlug == null || normalizedSlug.isBlank()
                ? "partner-property" : normalizedSlug.replace(' ', '-');
        property.setSlug((slugBase)
                + "-" + uniqueSuffix);
        property.setProvinceId(province.getId());
        property.setWardId(ward.getId());
        property.setAddressLine(normalizeDisplayText(requestedAddress));
        property.setCity(province.getNameVi());
        property.setCountry("Việt Nam");
        property.setPropertyType("HOTEL");
        property.setPhone(user.getPhone());
        property.setEmail(normalizeEmail(user.getEmail()));
        property.setStatus("DRAFT");
        property.setApprovalStatus("DRAFT");
        property.setOperationStatus("INACTIVE");
        property.setIsDemo(false);
        property.setDataSource("USER");
        return hotelRepository.saveAndFlush(property);
    }

    private Location requireActiveLocation(Long id, String type, String errorMessage) {
        return locationRepository.findById(id)
                .filter(location -> type.equals(location.getLocationType()))
                .filter(location -> "ACTIVE".equals(location.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    private LocationSelection requireValidLocationSelection(Long provinceId, Long wardId) {
        Location province = requireActiveLocation(provinceId, "PROVINCE", "Province is invalid.");
        Location ward = requireActiveLocation(wardId, "WARD", "Ward is invalid.");
        if (ward.getParent() == null || !province.getId().equals(ward.getParent().getId())) {
            throw new IllegalArgumentException("Ward does not belong to the selected province.");
        }
        return new LocationSelection(province, ward);
    }

    private record LocationSelection(Location province, Location ward) { }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new AccessDeniedException("Authenticated account email is unavailable.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    @Transactional
    public Hotel approveProperty(Long propertyId) {
        Hotel before = hotelRepository.findById(propertyId).orElse(null);
        Hotel saved = ownershipLifecycleService.approveProperty(propertyId);
        audit("PROPERTY_APPROVED", saved, before, "Property approval completed");
        return saved;
    }

    @Transactional
    public Hotel rejectProperty(Long propertyId) {
        Hotel before = hotelRepository.findById(propertyId).orElse(null);
        Hotel saved = ownershipLifecycleService.rejectProperty(propertyId);
        audit("PROPERTY_REJECTED", saved, before, "Property approval rejected");
        return saved;
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> registrationStatus(String username) {
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        var mappings = userPropertyRepository.findByUserId(user.getId());
        String status = mappings.stream().anyMatch(item -> "PENDING_APPROVAL".equals(item.getHotel().getApprovalStatus()))
                ? "PENDING" : mappings.isEmpty() ? "NONE" : "APPROVED";
        return java.util.Map.of("status", status, "propertyCount", mappings.size());
    }

    private void audit(String eventType, Hotel hotel, Hotel before, String reason) {
        if (operationalAuditService == null || hotel == null) return;
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "SYSTEM", null, "PROPERTY", eventType, "HOTEL", String.valueOf(hotel.getId()),
                null, null, reason, propertySnapshot(before), propertySnapshot(hotel), null));
    }

    private java.util.Map<String, Object> propertySnapshot(Hotel hotel) {
        if (hotel == null) return null;
        return java.util.Map.of("id", hotel.getId(), "name", hotel.getName(),
                "approvalStatus", hotel.getApprovalStatus(), "operationStatus", hotel.getOperationStatus(),
                "status", hotel.getStatus());
    }
}

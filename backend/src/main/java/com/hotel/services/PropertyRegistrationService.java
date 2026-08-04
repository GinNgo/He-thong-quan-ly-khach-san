package com.hotel.services;

import com.hotel.dtos.PartnerRegistrationRequest;
import com.hotel.dtos.PartnerRegistrationResponse;
import com.hotel.dtos.PartnerConversionRequest;
import com.hotel.dtos.PartnerRegistrationStatusResponse;
import com.hotel.entities.*;
import com.hotel.exceptions.RegistrationConflictException;
import com.hotel.repositories.*;
import com.hotel.util.VietnameseTextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyRegistrationService {

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final LocationRepository locationRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final PropertyClaimRequestRepository propertyClaimRequestRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final PropertyOwnershipLifecycleService ownershipLifecycleService;

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

    @Transactional(readOnly = true)
    public PartnerRegistrationStatusResponse registrationStatus(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authenticated account id is required.");
        }

        List<UserProperty> ownerMappings = userPropertyRepository.findOwnerMappingsWithHotelByUserId(userId);
        if (ownerMappings.isEmpty()) {
            return new PartnerRegistrationStatusResponse("NONE", 0, List.of());
        }

        Map<Long, UserProperty> mappingByProperty = new LinkedHashMap<>();
        ownerMappings.forEach(mapping -> {
            Hotel property = mapping.getHotel();
            if (property != null && property.getId() != null) {
                mappingByProperty.putIfAbsent(property.getId(), mapping);
            }
        });
        List<UserProperty> mappings = List.copyOf(mappingByProperty.values());
        if (mappings.isEmpty()) {
            return new PartnerRegistrationStatusResponse("NONE", 0, List.of());
        }

        List<Long> propertyIds = mappings.stream()
                .map(UserProperty::getHotel)
                .filter(Objects::nonNull)
                .map(Hotel::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ClaimStatus> latestClaims = latestClaims(userId, propertyIds);

        List<PartnerRegistrationStatusResponse.PropertyStatus> properties = mappings.stream()
                .map(mapping -> toRegistrationStatus(mapping, latestClaims))
                .toList();
        String overallStatus = properties.stream()
                .map(PartnerRegistrationStatusResponse.PropertyStatus::status)
                .distinct()
                .limit(2)
                .count() == 1
                ? properties.getFirst().status()
                : "MIXED";
        return new PartnerRegistrationStatusResponse(overallStatus, properties.size(), properties);
    }

    private Map<Long, ClaimStatus> latestClaims(Long userId, List<Long> propertyIds) {
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ClaimStatus> claims = new LinkedHashMap<>();
        propertyClaimRequestRepository
                .findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(userId, propertyIds)
                .forEach(claim -> {
                    Long propertyId = claim.getProperty() == null ? null : claim.getProperty().getId();
                    Long requesterId = claim.getRequesterUser() == null ? null : claim.getRequesterUser().getId();
                    if (claim.getId() != null
                            && userId.equals(requesterId)
                            && propertyIds.contains(propertyId)) {
                        claims.putIfAbsent(propertyId, new ClaimStatus(
                                claim.getId(),
                                canonicalRawStatus(claim.getStatus()),
                                normalizeOptionalReason(claim.getRejectionReason())));
                    }
                });
        return claims;
    }

    private PartnerRegistrationStatusResponse.PropertyStatus toRegistrationStatus(
            UserProperty mapping,
            Map<Long, ClaimStatus> latestClaims) {
        Hotel property = mapping.getHotel();
        ClaimStatus claim = property == null ? null : latestClaims.get(property.getId());
        String status = classifyRegistrationStatus(property, mapping, claim);
        String reason = null;
        if ("REJECTED".equals(status)) {
            reason = normalizeOptionalReason(mapping.getStatusReason());
            if (reason == null && claim != null) {
                reason = claim.rejectionReason();
            }
        } else if (("SUSPENDED".equals(status) || "CANCELLED".equals(status))
                && property != null) {
            reason = normalizeOptionalReason(property.getLifecycleReason());
        }
        return new PartnerRegistrationStatusResponse.PropertyStatus(
                property == null ? null : property.getId(),
                property == null ? null : property.getName(),
                status,
                property == null ? null : property.getApprovalStatus(),
                property == null ? null : property.getOperationStatus(),
                mapping.getStatus(),
                reason,
                claim == null ? null : claim.id(),
                claim == null ? null : claim.status());
    }

    private String classifyRegistrationStatus(
            Hotel property,
            UserProperty mapping,
            ClaimStatus claim) {
        String approval = canonicalRawStatus(property == null ? null : property.getApprovalStatus());
        String operation = canonicalRawStatus(property == null ? null : property.getOperationStatus());
        String ownership = canonicalRawStatus(mapping.getStatus());
        String claimStatus = claim == null ? "" : claim.status();

        if ("REJECTED".equals(claimStatus)) {
            return "REJECTED";
        }
        if ("CANCELLED".equals(claimStatus)) {
            return "CANCELLED";
        }
        if ("REJECTED".equals(approval)) {
            return "REJECTED";
        }
        if ("SUSPENDED".equals(approval) || "SUSPENDED".equals(operation) || "SUSPENDED".equals(ownership)) {
            return "SUSPENDED";
        }
        if (isCancelledStatus(approval) || isCancelledStatus(operation) || isCancelledStatus(ownership)
                || "INACTIVE".equals(ownership)) {
            return "CANCELLED";
        }
        if ("APPROVED".equals(approval)
                && "ACTIVE".equals(operation)
                && "ACTIVE".equals(ownership)) {
            return "APPROVED";
        }
        if ("PENDING".equals(claimStatus)
                || "PENDING_APPROVAL".equals(approval)
                || "PENDING".equals(ownership)) {
            return "PENDING";
        }
        if ("DRAFT".equals(approval)) {
            return "DRAFT";
        }
        return "CANCELLED";
    }

    private boolean isCancelledStatus(String status) {
        return "CANCELLED".equals(status) || "CLOSED".equals(status);
    }

    private String canonicalRawStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalReason(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ClaimStatus(Long id, String status, String rejectionReason) { }

}

package com.hotel.services;

import com.hotel.dtos.PropertyClaimResponseDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PropertyClaimService {

    private final PropertyClaimRequestRepository claimRepository;
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PropertyOwnershipLifecycleService ownershipLifecycleService;

    @Transactional
    public PropertyClaimResponseDTO requestClaim(Long propertyId, Long userId, String verificationMethod, String verificationData, String note) {
        Hotel property = hotelRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"IMPORTED_PENDING_REVIEW".equals(property.getApprovalStatus())) {
            throw new IllegalStateException("Property is not available for claiming.");
        }

        boolean alreadyPending = claimRepository.existsByPropertyIdAndRequesterUserIdAndStatus(propertyId, userId, "PENDING");
        if (alreadyPending) {
            throw new IllegalStateException("You already have a pending claim request for this property.");
        }

        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setProperty(property);
        claim.setRequesterUser(user);
        claim.setVerificationMethod(verificationMethod);
        claim.setVerificationData(verificationData);
        claim.setNote(note);
        claim.setStatus("PENDING");

        PropertyClaimRequest saved = claimRepository.save(claim);
        ownershipLifecycleService.createPendingOwner(user, property);
        return toResponse(saved);
    }

    @Transactional
    public PropertyClaimResponseDTO approveClaim(Long claimId, Long adminUserId) {
        PropertyClaimRequest claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim request not found"));
        
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        if (!"PENDING".equals(claim.getStatus())) {
            throw new IllegalStateException("Claim is not in PENDING state.");
        }

        Hotel property = claim.getProperty();
        User requester = claim.getRequesterUser();
        long currentProperties = userPropertyRepository.countActiveOwnedPropertiesByUserId(requester.getId());
        subscriptionFeatureService.checkFeatureLimit(
                requester.getId(), "MAX_PROPERTIES", currentProperties, 1);
        ownershipLifecycleService.activateOwner(property.getId(), requester.getId());

        // Update Claim Status
        claim.setStatus("APPROVED");
        claim.setReviewedBy(admin);
        claim.setReviewedAt(LocalDateTime.now());
        claimRepository.save(claim);

        // Update Property Status
        property.setApprovalStatus("APPROVED");
        property.setStatus("ACTIVE");
        property.setOperationStatus("ACTIVE");
        hotelRepository.save(property);

        return toResponse(claim);
    }

    @Transactional
    public PropertyClaimResponseDTO rejectClaim(Long claimId, Long adminUserId, String reason) {
        PropertyClaimRequest claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim request not found"));
        
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        if (!"PENDING".equals(claim.getStatus())) {
            throw new IllegalStateException("Claim is not in PENDING state.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required.");
        }

        claim.setStatus("REJECTED");
        claim.setReviewedBy(admin);
        claim.setReviewedAt(LocalDateTime.now());
        claim.setRejectionReason(reason);
        ownershipLifecycleService.deactivatePendingOwner(
                claim.getProperty().getId(), claim.getRequesterUser().getId());
        return toResponse(claimRepository.save(claim));
    }

    @Transactional
    public PropertyClaimResponseDTO cancelClaim(Long claimId, Long requesterUserId) {
        PropertyClaimRequest claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim request not found"));
        if (!claim.getRequesterUser().getId().equals(requesterUserId)) {
            throw new SecurityException("Bạn không có quyền huỷ yêu cầu này.");
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new IllegalStateException("Chỉ có thể huỷ yêu cầu đang chờ duyệt.");
        }
        claim.setStatus("CANCELLED");
        ownershipLifecycleService.deactivatePendingOwner(
                claim.getProperty().getId(), requesterUserId);
        return toResponse(claimRepository.save(claim));
    }

    @Transactional(readOnly = true)
    public Page<PropertyClaimResponseDTO> listClaims(String status, Pageable pageable) {
        Page<PropertyClaimRequest> claims = status == null || status.isBlank()
                ? claimRepository.findAll(pageable)
                : claimRepository.findByStatus(status, pageable);
        return claims.map(this::toResponse);
    }

    private PropertyClaimResponseDTO toResponse(PropertyClaimRequest claim) {
        Hotel property = claim.getProperty();
        User requester = claim.getRequesterUser();
        User reviewer = claim.getReviewedBy();
        return new PropertyClaimResponseDTO(
                claim.getId(),
                property == null ? null : new PropertyClaimResponseDTO.PropertySummary(
                        property.getId(), property.getCode(), property.getName(),
                        property.getApprovalStatus(), property.getOperationStatus()),
                userSummary(requester),
                claim.getVerificationMethod(),
                claim.getVerificationData(),
                claim.getNote(),
                claim.getStatus(),
                userSummary(reviewer),
                claim.getReviewedAt(),
                claim.getRejectionReason(),
                claim.getCreatedAt());
    }

    private PropertyClaimResponseDTO.UserSummary userSummary(User user) {
        if (user == null) return null;
        return new PropertyClaimResponseDTO.UserSummary(
                user.getId(), user.getUsername(), user.getEmail(), user.getFullName());
    }
}

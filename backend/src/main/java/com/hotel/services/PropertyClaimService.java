package com.hotel.services;

import com.hotel.dtos.PropertyClaimResponseDTO;
import com.hotel.dtos.PropertyClaimRequestDTO;
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
    private final PropertyClaimRateLimiter rateLimiter;
    private final PropertyApprovalWorkflowService approvalWorkflowService;

    @Transactional
    public PropertyClaimResponseDTO requestClaim(Long propertyId, Long userId, PropertyClaimRequestDTO request) {
        PropertyClaimRequestDTO validatedRequest = requireValidRequest(request);
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

        rateLimiter.check(userId);

        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setProperty(property);
        claim.setRequesterUser(user);
        claim.setVerificationMethod(validatedRequest.verificationMethod());
        claim.setVerificationData(validatedRequest.verificationData());
        claim.setNote(validatedRequest.note());
        claim.setStatus("PENDING");

        PropertyClaimRequest saved = claimRepository.save(claim);
        ownershipLifecycleService.createPendingOwner(user, property);
        return toResponse(saved);
    }

    private PropertyClaimRequestDTO requireValidRequest(PropertyClaimRequestDTO request) {
        if (request == null) throw new IllegalArgumentException("Claim request is required.");
        return request.requireValid();
    }

    @Transactional
    public PropertyClaimResponseDTO approveClaim(Long claimId, Long adminUserId) {
        if (claimId == null) {
            throw new IllegalArgumentException("Claim request id is required.");
        }
        if (adminUserId == null) {
            throw new IllegalArgumentException("Admin user id is required.");
        }
        PropertyClaimRequest claim = claimRepository.findByIdForUpdate(claimId)
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
        var approval = approvalWorkflowService.approveImportedClaim(
                adminUserId, property.getId(), requester.getId(), claim.getId());

        claim.setStatus("APPROVED");
        claim.setReviewedBy(admin);
        claim.setReviewedAt(approval.reviewedAt());
        claimRepository.saveAndFlush(claim);

        return toResponse(claim);
    }

    @Transactional
    public PropertyClaimResponseDTO rejectClaim(Long claimId, Long adminUserId, String reason) {
        if (claimId == null) {
            throw new IllegalArgumentException("Claim request id is required.");
        }
        if (adminUserId == null) {
            throw new IllegalArgumentException("Admin user id is required.");
        }
        String validatedReason = requireValidRejectionReason(reason);
        PropertyClaimRequest claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim request not found"));

        if (!"PENDING".equals(claim.getStatus())) {
            throw new IllegalStateException("Claim is not in PENDING state.");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        if (!ownershipLifecycleService.deactivatePendingOwner(
                claim.getProperty().getId(), claim.getRequesterUser().getId())) {
            throw new IllegalStateException("Pending property ownership is no longer actionable.");
        }

        claim.setStatus("REJECTED");
        claim.setReviewedBy(admin);
        claim.setReviewedAt(LocalDateTime.now());
        claim.setRejectionReason(validatedReason);
        return toResponse(claimRepository.saveAndFlush(claim));
    }

    @Transactional
    public PropertyClaimResponseDTO cancelClaim(Long claimId, Long requesterUserId) {
        if (claimId == null) {
            throw new IllegalArgumentException("Claim request id is required.");
        }
        if (requesterUserId == null) {
            throw new SecurityException("Authenticated requester id is required.");
        }
        PropertyClaimRequest claim = claimRepository
                .findByIdAndRequesterUserIdForUpdate(claimId, requesterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Claim request not found"));
        if (!"PENDING".equals(claim.getStatus())) {
            throw new IllegalStateException("Only a pending property claim can be cancelled.");
        }
        if (!ownershipLifecycleService.deactivatePendingOwner(
                claim.getProperty().getId(), requesterUserId)) {
            throw new IllegalStateException("Pending property ownership is no longer actionable.");
        }
        claim.setStatus("CANCELLED");
        claim.setReviewedBy(null);
        claim.setReviewedAt(null);
        claim.setRejectionReason(null);
        return toResponse(claimRepository.saveAndFlush(claim));
    }

    private String requireValidRejectionReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new IllegalArgumentException(
                    "Rejection reason must contain between 10 and 500 characters.");
        }
        return normalized;
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
                        property.getStatus(),
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

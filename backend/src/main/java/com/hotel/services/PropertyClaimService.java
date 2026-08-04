package com.hotel.services;

import com.hotel.dtos.PropertyClaimRequestDTO;
import com.hotel.dtos.PropertyClaimResponseDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.User;
import com.hotel.exceptions.PropertyClaimConflictException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        Hotel property = hotelRepository.findByIdForUpdate(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"IMPORTED_PENDING_REVIEW".equals(property.getApprovalStatus())) {
            throw new IllegalStateException("Property is not available for claiming.");
        }
        if (!claimRepository.findPendingByPropertyIdAndRequesterUserIdForUpdate(propertyId, userId).isEmpty()) {
            throw PropertyClaimConflictException.alreadyPending();
        }

        rateLimiter.check(userId);
        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setProperty(property);
        claim.setRequesterUser(user);
        claim.setVerificationMethod(validatedRequest.verificationMethod());
        claim.setVerificationData(validatedRequest.verificationData());
        claim.setNote(validatedRequest.note());
        claim.setStatus("PENDING");

        try {
            PropertyClaimRequest saved = claimRepository.saveAndFlush(claim);
            ownershipLifecycleService.createPendingOwner(user, property);
            return toResponse(saved);
        } catch (DataIntegrityViolationException conflict) {
            throw PropertyClaimConflictException.concurrentConflict();
        }
    }

    private PropertyClaimRequestDTO requireValidRequest(PropertyClaimRequestDTO request) {
        if (request == null) throw new IllegalArgumentException("Claim request is required.");
        return request.requireValid();
    }

    @Transactional
    public PropertyClaimResponseDTO approveClaim(Long claimId, Long adminUserId) {
        requireClaimAndActorIds(claimId, adminUserId, "Admin user id is required.");
        LockedClaim locked = lockClaim(claimId, null);
        PropertyClaimRequest claim = locked.claim();
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        requirePending(claim);

        User requester = claim.getRequesterUser();
        long currentProperties = userPropertyRepository.countActiveOwnedPropertiesByUserId(requester.getId());
        subscriptionFeatureService.checkFeatureLimit(requester.getId(), "MAX_PROPERTIES", currentProperties, 1);
        var approval = approvalWorkflowService.approveImportedClaim(
                adminUserId, locked.property().getId(), requester.getId(), claim.getId());

        claim.setStatus("APPROVED");
        claim.setReviewedBy(admin);
        claim.setReviewedAt(approval.reviewedAt());
        claimRepository.saveAndFlush(claim);
        return toResponse(claim);
    }

    @Transactional
    public PropertyClaimResponseDTO rejectClaim(Long claimId, Long adminUserId, String reason) {
        requireClaimAndActorIds(claimId, adminUserId, "Admin user id is required.");
        String validatedReason = requireValidRejectionReason(reason);
        PropertyClaimRequest claim = lockClaim(claimId, null).claim();
        requirePending(claim);
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        if (!ownershipLifecycleService.deactivatePendingOwner(
                claim.getProperty().getId(), claim.getRequesterUser().getId())) {
            throw PropertyClaimConflictException.concurrentConflict();
        }

        claim.setStatus("REJECTED");
        claim.setReviewedBy(admin);
        claim.setReviewedAt(LocalDateTime.now());
        claim.setRejectionReason(validatedReason);
        return toResponse(claimRepository.saveAndFlush(claim));
    }

    @Transactional
    public PropertyClaimResponseDTO cancelClaim(Long claimId, Long requesterUserId) {
        if (claimId == null) throw new IllegalArgumentException("Claim request id is required.");
        if (requesterUserId == null) throw new SecurityException("Authenticated requester id is required.");
        PropertyClaimRequest claim = lockClaim(claimId, requesterUserId).claim();
        requirePending(claim);
        if (!ownershipLifecycleService.deactivatePendingOwner(claim.getProperty().getId(), requesterUserId)) {
            throw PropertyClaimConflictException.concurrentConflict();
        }
        claim.setStatus("CANCELLED");
        claim.setReviewedBy(null);
        claim.setReviewedAt(null);
        claim.setRejectionReason(null);
        return toResponse(claimRepository.saveAndFlush(claim));
    }

    private LockedClaim lockClaim(Long claimId, Long requesterUserId) {
        Long propertyId = requesterUserId == null
                ? claimRepository.findPropertyIdById(claimId).orElseThrow(this::claimNotFound)
                : claimRepository.findPropertyIdByIdAndRequesterUserId(claimId, requesterUserId)
                        .orElseThrow(this::claimNotFound);
        Hotel property = hotelRepository.findByIdForUpdate(propertyId).orElseThrow(this::claimNotFound);
        PropertyClaimRequest claim = requesterUserId == null
                ? claimRepository.findByIdForUpdate(claimId).orElseThrow(this::claimNotFound)
                : claimRepository.findByIdAndRequesterUserIdForUpdate(claimId, requesterUserId)
                        .orElseThrow(this::claimNotFound);
        if (claim.getProperty() == null
                || !propertyId.equals(claim.getProperty().getId())
                || !propertyId.equals(property.getId())) {
            throw PropertyClaimConflictException.concurrentConflict();
        }
        return new LockedClaim(property, claim);
    }

    private void requirePending(PropertyClaimRequest claim) {
        if (!"PENDING".equals(claim.getStatus())) {
            throw PropertyClaimConflictException.notPending(claim.getStatus());
        }
    }

    private void requireClaimAndActorIds(Long claimId, Long actorId, String actorMessage) {
        if (claimId == null) throw new IllegalArgumentException("Claim request id is required.");
        if (actorId == null) throw new IllegalArgumentException(actorMessage);
    }

    private IllegalArgumentException claimNotFound() {
        return new IllegalArgumentException("Claim request not found");
    }

    private String requireValidRejectionReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new IllegalArgumentException("Rejection reason must contain between 10 and 500 characters.");
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
                        property.getId(), property.getCode(), property.getName(), property.getStatus(),
                        property.getApprovalStatus(), property.getOperationStatus()),
                userSummary(requester), claim.getVerificationMethod(), claim.getVerificationData(), claim.getNote(),
                claim.getStatus(), userSummary(reviewer), claim.getReviewedAt(), claim.getRejectionReason(),
                claim.getCreatedAt());
    }

    private PropertyClaimResponseDTO.UserSummary userSummary(User user) {
        if (user == null) return null;
        return new PropertyClaimResponseDTO.UserSummary(
                user.getId(), user.getUsername(), user.getEmail(), user.getFullName());
    }

    private record LockedClaim(Hotel property, PropertyClaimRequest claim) {}
}

package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public PropertyClaimRequest requestClaim(Long propertyId, Long userId, String verificationMethod, String verificationData, String note) {
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

        return claimRepository.save(claim);
    }

    @Transactional
    public PropertyClaimRequest approveClaim(Long claimId, Long adminUserId) {
        PropertyClaimRequest claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim request not found"));
        
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        if (!"PENDING".equals(claim.getStatus())) {
            throw new IllegalStateException("Claim is not in PENDING state.");
        }

        Hotel property = claim.getProperty();
        User requester = claim.getRequesterUser();

        // Update Claim Status
        claim.setStatus("APPROVED");
        claim.setReviewedBy(admin);
        claim.setReviewedAt(LocalDateTime.now());
        claimRepository.save(claim);

        // Update Property Status
        property.setApprovalStatus("ACTIVE");
        property.setStatus("ACTIVE");
        hotelRepository.save(property);

        // Create UserProperty mapping for OWNER
        UserProperty userProperty = new UserProperty();
        userProperty.setHotel(property);
        userProperty.setUser(requester);
        userProperty.setRelationshipType("OWNER");
        userPropertyRepository.save(userProperty);

        return claim;
    }

    @Transactional
    public PropertyClaimRequest rejectClaim(Long claimId, Long adminUserId, String reason) {
        PropertyClaimRequest claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim request not found"));
        
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        claim.setStatus("REJECTED");
        claim.setReviewedBy(admin);
        claim.setReviewedAt(LocalDateTime.now());
        claim.setRejectionReason(reason);
        
        return claimRepository.save(claim);
    }
}

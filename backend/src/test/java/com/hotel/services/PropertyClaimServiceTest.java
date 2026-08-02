package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.User;
import com.hotel.dtos.PropertyClaimResponseDTO;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyClaimServiceTest {
    @Mock private PropertyClaimRequestRepository claimRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private SubscriptionFeatureService subscriptionFeatureService;
    @Mock private PropertyOwnershipLifecycleService ownershipLifecycleService;

    @InjectMocks
    private PropertyClaimService claimService;

    @Test
    void approveClaim_WhenRequesterExceedsPropertyQuota_DoesNotMutateClaimOrProperty() {
        User requester = new User();
        requester.setId(7L);
        User admin = new User();
        admin.setId(1L);
        Hotel property = new Hotel();
        property.setId(10L);
        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setId(20L);
        claim.setStatus("PENDING");
        claim.setRequesterUser(requester);
        claim.setProperty(property);

        when(claimRepository.findById(20L)).thenReturn(Optional.of(claim));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userPropertyRepository.countActiveOwnedPropertiesByUserId(7L)).thenReturn(1L);
        doThrow(new RuntimeException("quota exceeded"))
                .when(subscriptionFeatureService).checkFeatureLimit(7L, "MAX_PROPERTIES", 1L, 1L);

        assertThrows(RuntimeException.class, () -> claimService.approveClaim(20L, 1L));

        verify(claimRepository, never()).save(any());
        verify(hotelRepository, never()).save(any());
        verify(userPropertyRepository, never()).save(any());
    }

    @Test
    void requestClaim_CreatesPendingOwnerWithoutGrantingOperationalAccess() {
        User requester = user(7L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(property));
        when(userRepository.findById(7L)).thenReturn(Optional.of(requester));
        when(claimRepository.save(any(PropertyClaimRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyClaimResponseDTO result = claimService.requestClaim(
                10L, 7L, "EMAIL", "owner@example.com", "Verify ownership");

        assertEquals("PENDING", result.status());
        verify(ownershipLifecycleService).createPendingOwner(requester, property);
        verify(ownershipLifecycleService, never()).activateOwner(any(), any());
    }

    @Test
    void approveClaim_ActivatesMappingAndCanonicalPropertyState() {
        User requester = user(7L);
        User admin = user(1L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest claim = claim(20L, "PENDING", requester, property);
        when(claimRepository.findById(20L)).thenReturn(Optional.of(claim));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        claimService.approveClaim(20L, 1L);

        assertEquals("APPROVED", claim.getStatus());
        assertEquals("APPROVED", property.getApprovalStatus());
        assertEquals("ACTIVE", property.getOperationStatus());
        verify(ownershipLifecycleService).activateOwner(10L, 7L);
    }

    @Test
    void rejectClaim_OnlyRejectsPendingAndExpiresApplicantMapping() {
        User requester = user(7L);
        User admin = user(1L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest claim = claim(20L, "PENDING", requester, property);
        when(claimRepository.findById(20L)).thenReturn(Optional.of(claim));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(claimRepository.save(claim)).thenReturn(claim);

        PropertyClaimResponseDTO result = claimService.rejectClaim(20L, 1L, "Insufficient evidence");

        assertEquals("REJECTED", result.status());
        verify(ownershipLifecycleService).deactivatePendingOwner(10L, 7L);
    }

    @Test
    void cancelClaim_RequiresRequesterAndExpiresApplicantMapping() {
        User requester = user(7L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest claim = claim(20L, "PENDING", requester, property);
        when(claimRepository.findById(20L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(claim)).thenReturn(claim);

        PropertyClaimResponseDTO result = claimService.cancelClaim(20L, 7L);

        assertEquals("CANCELLED", result.status());
        verify(ownershipLifecycleService).deactivatePendingOwner(10L, 7L);
        assertThrows(SecurityException.class, () -> claimService.cancelClaim(20L, 99L));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Hotel hotel(Long id, String approvalStatus) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setApprovalStatus(approvalStatus);
        hotel.setOperationStatus("INACTIVE");
        return hotel;
    }

    private PropertyClaimRequest claim(Long id, String status, User requester, Hotel property) {
        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setId(id);
        claim.setStatus(status);
        claim.setRequesterUser(requester);
        claim.setProperty(property);
        return claim;
    }
}

package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.User;
import com.hotel.dtos.PropertyClaimRequestDTO;
import com.hotel.dtos.PropertyClaimResponseDTO;
import com.hotel.exceptions.PropertyClaimRateLimitException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private PropertyClaimRateLimiter rateLimiter;
    @Mock private PropertyApprovalWorkflowService approvalWorkflowService;

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

        mockAdminClaimLock(claim);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userPropertyRepository.countActiveOwnedPropertiesByUserId(7L)).thenReturn(1L);
        doThrow(new RuntimeException("quota exceeded"))
                .when(subscriptionFeatureService).checkFeatureLimit(7L, "MAX_PROPERTIES", 1L, 1L);

        assertThrows(RuntimeException.class, () -> claimService.approveClaim(20L, 1L));

        verify(claimRepository, never()).save(any());
        verify(claimRepository, never()).saveAndFlush(any());
        verify(hotelRepository, never()).save(any());
        verify(userPropertyRepository, never()).save(any());
        verifyNoInteractions(approvalWorkflowService);
    }

    @Test
    void requestClaim_CreatesPendingOwnerWithoutGrantingOperationalAccess() {
        User requester = user(7L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(property));
        when(userRepository.findById(7L)).thenReturn(Optional.of(requester));
        when(claimRepository.saveAndFlush(any(PropertyClaimRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyClaimResponseDTO result = claimService.requestClaim(
                10L,
                7L,
                new PropertyClaimRequestDTO(
                        " email ", " owner@example.com ", "   "));

        assertEquals("PENDING", result.status());
        assertEquals("EMAIL", result.verificationMethod());
        assertEquals("owner@example.com", result.verificationData());
        assertNull(result.note());
        InOrder mutationOrder = inOrder(rateLimiter, claimRepository, ownershipLifecycleService);
        mutationOrder.verify(rateLimiter).check(7L);
        mutationOrder.verify(claimRepository).saveAndFlush(any(PropertyClaimRequest.class));
        mutationOrder.verify(ownershipLifecycleService).createPendingOwner(requester, property);
        verify(ownershipLifecycleService).createPendingOwner(requester, property);
        verify(ownershipLifecycleService, never()).activateOwner(any(), any());
    }

    @Test
    void requestClaim_WhenAccountRateLimited_CreatesNoClaimOrPendingOwner() {
        User requester = user(7L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(property));
        when(userRepository.findById(7L)).thenReturn(Optional.of(requester));
        doThrow(new PropertyClaimRateLimitException(90)).when(rateLimiter).check(7L);

        PropertyClaimRateLimitException exception = assertThrows(
                PropertyClaimRateLimitException.class,
                () -> claimService.requestClaim(
                        10L,
                        7L,
                        new PropertyClaimRequestDTO("PHONE", "+84 901 234 567", null)));

        assertEquals(90, exception.getRetryAfterSeconds());
        verify(claimRepository, never()).save(any());
        verify(ownershipLifecycleService, never()).createPendingOwner(any(), any());
    }

    @Test
    void requestClaim_RejectsInvalidDirectDtosBeforeAnyRepositoryCall() {
        PropertyClaimRequestDTO invalidMethod = new PropertyClaimRequestDTO("FAX", "proof", null);
        PropertyClaimRequestDTO blankData = new PropertyClaimRequestDTO("EMAIL", "   ", null);
        PropertyClaimRequestDTO oversizedData = new PropertyClaimRequestDTO(
                "EMAIL", "x".repeat(1001), null);
        PropertyClaimRequestDTO oversizedNote = new PropertyClaimRequestDTO(
                "EMAIL", "proof", "x".repeat(501));

        assertThrows(IllegalArgumentException.class,
                () -> claimService.requestClaim(10L, 7L, invalidMethod));
        assertThrows(IllegalArgumentException.class,
                () -> claimService.requestClaim(10L, 7L, blankData));
        assertThrows(IllegalArgumentException.class,
                () -> claimService.requestClaim(10L, 7L, oversizedData));
        assertThrows(IllegalArgumentException.class,
                () -> claimService.requestClaim(10L, 7L, oversizedNote));

        verifyNoInteractions(
                claimRepository,
                hotelRepository,
                userRepository,
                userPropertyRepository,
                subscriptionFeatureService,
                ownershipLifecycleService,
                rateLimiter);
    }

    @Test
    void approveClaim_ActivatesMappingAndCanonicalPropertyState() {
        User requester = user(7L);
        User admin = user(1L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest claim = claim(20L, "PENDING", requester, property);
        LocalDateTime reviewedAt = LocalDateTime.of(2026, 8, 4, 7, 15);
        mockAdminClaimLock(claim);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(approvalWorkflowService.approveImportedClaim(1L, 10L, 7L, 20L))
                .thenReturn(new com.hotel.dtos.PropertyApprovalDecisionResponse(
                        10L, "ACTIVE", "APPROVED", "ACTIVE", "ACTIVE", 1L, reviewedAt, null));

        claimService.approveClaim(20L, 1L);

        assertEquals("APPROVED", claim.getStatus());
        assertEquals(admin, claim.getReviewedBy());
        assertEquals(reviewedAt, claim.getReviewedAt());
        assertEquals("DRAFT", property.getStatus());
        verify(approvalWorkflowService).approveImportedClaim(1L, 10L, 7L, 20L);
        verify(claimRepository).saveAndFlush(claim);
        verify(ownershipLifecycleService, never()).activateOwner(any(), any());
    }

    @Test
    void approveClaim_RejectsNonPendingClaimBeforeQuotaOrWorkflowMutation() {
        User requester = user(7L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest claim = claim(20L, "APPROVED", requester, property);
        mockAdminClaimLock(claim);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

        assertThrows(IllegalStateException.class, () -> claimService.approveClaim(20L, 1L));

        verifyNoInteractions(subscriptionFeatureService, approvalWorkflowService);
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectClaim_OnlyRejectsPendingAndExpiresApplicantMapping() {
        User requester = user(7L);
        User admin = user(1L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest claim = claim(20L, "PENDING", requester, property);
        mockAdminClaimLock(claim);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(ownershipLifecycleService.deactivatePendingOwner(10L, 7L)).thenReturn(true);
        when(claimRepository.saveAndFlush(claim)).thenReturn(claim);

        PropertyClaimResponseDTO result = claimService.rejectClaim(
                20L, 1L, "  Insufficient evidence  ");

        assertEquals("REJECTED", result.status());
        assertEquals("Insufficient evidence", result.rejectionReason());
        assertEquals("DRAFT", result.property().status());
        assertEquals("IMPORTED_PENDING_REVIEW", result.property().approvalStatus());
        assertEquals("INACTIVE", result.property().operationStatus());
        verify(ownershipLifecycleService).deactivatePendingOwner(10L, 7L);
        verify(claimRepository).saveAndFlush(claim);
    }

    @Test
    void cancelClaim_RequiresRequesterAndExpiresApplicantMapping() {
        User requester = user(7L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest claim = claim(20L, "PENDING", requester, property);
        mockRequesterClaimLock(claim, 7L);
        when(ownershipLifecycleService.deactivatePendingOwner(10L, 7L)).thenReturn(true);
        when(claimRepository.saveAndFlush(claim)).thenReturn(claim);

        PropertyClaimResponseDTO result = claimService.cancelClaim(20L, 7L);

        assertEquals("CANCELLED", result.status());
        assertNull(result.reviewedAt());
        assertNull(result.reviewedBy());
        assertNull(result.rejectionReason());
        assertEquals("DRAFT", result.property().status());
        assertEquals("IMPORTED_PENDING_REVIEW", result.property().approvalStatus());
        assertEquals("INACTIVE", result.property().operationStatus());
        verify(ownershipLifecycleService).deactivatePendingOwner(10L, 7L);
        verify(claimRepository).saveAndFlush(claim);
    }

    @Test
    void rejectClaim_ValidatesTrimmedReasonBeforeAnyRepositoryCall() {
        assertThrows(IllegalArgumentException.class,
                () -> claimService.rejectClaim(20L, 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> claimService.rejectClaim(20L, 1L, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> claimService.rejectClaim(20L, 1L, " too short "));
        assertThrows(IllegalArgumentException.class,
                () -> claimService.rejectClaim(20L, 1L, "x".repeat(501)));

        verifyNoInteractions(claimRepository, userRepository, ownershipLifecycleService);
    }

    @Test
    void rejectClaim_FailsClosedWhenClaimOrPendingOwnershipIsNoLongerActionable() {
        User requester = user(7L);
        User admin = user(1L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest approved = claim(20L, "APPROVED", requester, property);
        mockAdminClaimLock(approved);

        assertThrows(IllegalStateException.class,
                () -> claimService.rejectClaim(20L, 1L, "Ownership evidence is invalid"));
        verifyNoInteractions(userRepository, ownershipLifecycleService);
        verify(claimRepository, never()).saveAndFlush(any());

        PropertyClaimRequest pending = claim(21L, "PENDING", requester, property);
        mockAdminClaimLock(pending);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(ownershipLifecycleService.deactivatePendingOwner(10L, 7L)).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> claimService.rejectClaim(21L, 1L, "Ownership evidence is invalid"));
        assertEquals("PENDING", pending.getStatus());
        assertNull(pending.getReviewedAt());
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelClaim_HidesForeignAndMissingClaimsBehindTheSameNotFoundBoundary() {
        when(claimRepository.findPropertyIdByIdAndRequesterUserId(20L, 99L)).thenReturn(Optional.empty());
        when(claimRepository.findPropertyIdByIdAndRequesterUserId(404L, 99L)).thenReturn(Optional.empty());

        IllegalArgumentException foreign = assertThrows(
                IllegalArgumentException.class, () -> claimService.cancelClaim(20L, 99L));
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class, () -> claimService.cancelClaim(404L, 99L));

        assertEquals("Claim request not found", foreign.getMessage());
        assertEquals(foreign.getMessage(), missing.getMessage());
        verifyNoInteractions(ownershipLifecycleService);
        verify(claimRepository, never()).findByIdForUpdate(any());
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelClaim_FailsClosedForReviewedClaimOrMissingPendingOwnership() {
        User requester = user(7L);
        Hotel property = hotel(10L, "IMPORTED_PENDING_REVIEW");
        PropertyClaimRequest reviewed = claim(20L, "REJECTED", requester, property);
        mockRequesterClaimLock(reviewed, 7L);

        assertThrows(IllegalStateException.class, () -> claimService.cancelClaim(20L, 7L));
        verifyNoInteractions(ownershipLifecycleService);

        PropertyClaimRequest pending = claim(21L, "PENDING", requester, property);
        mockRequesterClaimLock(pending, 7L);
        when(ownershipLifecycleService.deactivatePendingOwner(10L, 7L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> claimService.cancelClaim(21L, 7L));
        assertEquals("PENDING", pending.getStatus());
        verify(claimRepository, never()).saveAndFlush(any());
    }

    private void mockAdminClaimLock(PropertyClaimRequest claim) {
        Long propertyId = claim.getProperty().getId();
        when(claimRepository.findPropertyIdById(claim.getId())).thenReturn(Optional.of(propertyId));
        when(hotelRepository.findByIdForUpdate(propertyId)).thenReturn(Optional.of(claim.getProperty()));
        when(claimRepository.findByIdForUpdate(claim.getId())).thenReturn(Optional.of(claim));
    }

    private void mockRequesterClaimLock(PropertyClaimRequest claim, Long requesterUserId) {
        Long propertyId = claim.getProperty().getId();
        when(claimRepository.findPropertyIdByIdAndRequesterUserId(claim.getId(), requesterUserId))
                .thenReturn(Optional.of(propertyId));
        when(hotelRepository.findByIdForUpdate(propertyId)).thenReturn(Optional.of(claim.getProperty()));
        when(claimRepository.findByIdAndRequesterUserIdForUpdate(claim.getId(), requesterUserId))
                .thenReturn(Optional.of(claim));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Hotel hotel(Long id, String approvalStatus) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setStatus("DRAFT");
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

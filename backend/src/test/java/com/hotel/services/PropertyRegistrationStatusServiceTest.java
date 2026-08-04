package com.hotel.services;

import com.hotel.dtos.PartnerRegistrationStatusResponse;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyRegistrationStatusServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private PropertyClaimRequestRepository propertyClaimRequestRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PropertyOwnershipLifecycleService ownershipLifecycleService;

    @InjectMocks
    private PropertyRegistrationService registrationService;

    @Test
    void missingAuthoritativeUserIdIsRejected() {
        assertThrows(AccessDeniedException.class, () -> registrationService.registrationStatus(null));
        verify(userPropertyRepository, never()).findOwnerMappingsWithHotelByUserId(null);
    }

    @Test
    void noOwnerMappingsReturnsNoneWithoutClaimLookup() {
        when(userPropertyRepository.findOwnerMappingsWithHotelByUserId(42L)).thenReturn(List.of());

        PartnerRegistrationStatusResponse result = registrationService.registrationStatus(42L);

        assertEquals("NONE", result.overallStatus());
        assertEquals(0, result.propertyCount());
        assertEquals(List.of(), result.properties());
        verify(propertyClaimRequestRepository, never())
                .findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(42L, List.of());
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, INACTIVE, PENDING, PENDING",
            "DRAFT, INACTIVE, ACTIVE, DRAFT",
            "PENDING_APPROVAL, INACTIVE, PENDING, PENDING",
            "APPROVED, ACTIVE, ACTIVE, APPROVED",
            "REJECTED, INACTIVE, INACTIVE, REJECTED",
            "APPROVED, SUSPENDED, ACTIVE, SUSPENDED",
            "APPROVED, ACTIVE, SUSPENDED, SUSPENDED",
            "APPROVED, INACTIVE, INACTIVE, CANCELLED",
            "APPROVED, ACTIVE, INACTIVE, CANCELLED",
            "APPROVED, INACTIVE, ACTIVE, CANCELLED"
    })
    void mapsCanonicalPerPropertyStates(
            String approvalStatus,
            String operationStatus,
            String ownershipStatus,
            String expectedStatus) {
        UserProperty mapping = ownerMapping(11L, "Harbor Hotel", approvalStatus, operationStatus, ownershipStatus);
        when(userPropertyRepository.findOwnerMappingsWithHotelByUserId(42L)).thenReturn(List.of(mapping));
        when(propertyClaimRequestRepository.findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(42L, List.of(11L)))
                .thenReturn(List.of());

        PartnerRegistrationStatusResponse result = registrationService.registrationStatus(42L);

        assertEquals(expectedStatus, result.overallStatus());
        assertEquals(1, result.propertyCount());
        var row = result.properties().getFirst();
        assertEquals(expectedStatus, row.status());
        assertEquals(approvalStatus, row.approvalStatus());
        assertEquals(operationStatus, row.operationStatus());
        assertEquals(ownershipStatus, row.ownershipStatus());
        assertNull(row.rejectionReason());
        assertNull(row.claimId());
        assertNull(row.claimStatus());
    }

    @Test
    void mixedStateRetainsEveryOwnerMapping() {
        UserProperty draft = ownerMapping(11L, "Draft Hotel", "DRAFT", "INACTIVE", "PENDING");
        UserProperty approved = ownerMapping(12L, "Live Hotel", "APPROVED", "ACTIVE", "ACTIVE");
        when(userPropertyRepository.findOwnerMappingsWithHotelByUserId(42L))
                .thenReturn(List.of(draft, approved));
        when(propertyClaimRequestRepository.findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(
                42L, List.of(11L, 12L)))
                .thenReturn(List.of());

        PartnerRegistrationStatusResponse result = registrationService.registrationStatus(42L);

        assertEquals("MIXED", result.overallStatus());
        assertEquals(2, result.propertyCount());
        assertEquals(List.of(11L, 12L), result.properties().stream()
                .map(PartnerRegistrationStatusResponse.PropertyStatus::propertyId)
                .toList());
        assertEquals(List.of("PENDING", "APPROVED"), result.properties().stream()
                .map(PartnerRegistrationStatusResponse.PropertyStatus::status)
                .toList());
    }

    @Test
    void rejectionReasonUsesMappingFirstThenOnlyExactRequesterPropertyClaim() {
        UserProperty mappingReason = ownerMapping(11L, "Mapped", "REJECTED", "INACTIVE", "INACTIVE");
        mappingReason.setStatusReason("  Mapping rejection wins  ");
        UserProperty exactClaimReason = ownerMapping(12L, "Claimed", "REJECTED", "INACTIVE", "INACTIVE");
        UserProperty unsafeClaimReason = ownerMapping(13L, "Isolated", "REJECTED", "INACTIVE", "INACTIVE");

        when(userPropertyRepository.findOwnerMappingsWithHotelByUserId(42L))
                .thenReturn(List.of(mappingReason, exactClaimReason, unsafeClaimReason));
        when(propertyClaimRequestRepository.findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(
                42L, List.of(11L, 12L, 13L)))
                .thenReturn(List.of(
                        claim(90L, 99L, 13L, "REJECTED", "Other account reason"),
                        claim(91L, 42L, 999L, "REJECTED", "Other property reason"),
                        claim(92L, 42L, 12L, "REJECTED", "  Exact claim rejection  ")));

        PartnerRegistrationStatusResponse result = registrationService.registrationStatus(42L);

        assertEquals("Mapping rejection wins", result.properties().get(0).rejectionReason());
        assertEquals("Exact claim rejection", result.properties().get(1).rejectionReason());
        assertNull(result.properties().get(2).rejectionReason());
        assertEquals(92L, result.properties().get(1).claimId());
        assertEquals("REJECTED", result.properties().get(1).claimStatus());
    }

    @Test
    void importedClaimRowsExposeExactPendingRejectedAndCancelledClaimState() {
        UserProperty pending = ownerMapping(
                11L, "Pending Imported", "IMPORTED_PENDING_REVIEW", "ACTIVE", "PENDING");
        UserProperty rejected = ownerMapping(
                12L, "Rejected Imported", "IMPORTED_PENDING_REVIEW", "ACTIVE", "INACTIVE");
        UserProperty cancelled = ownerMapping(
                13L, "Cancelled Imported", "IMPORTED_PENDING_REVIEW", "ACTIVE", "INACTIVE");
        when(userPropertyRepository.findOwnerMappingsWithHotelByUserId(42L))
                .thenReturn(List.of(pending, rejected, cancelled));
        when(propertyClaimRequestRepository.findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(
                42L, List.of(11L, 12L, 13L)))
                .thenReturn(List.of(
                        claim(103L, 42L, 13L, "CANCELLED", null),
                        claim(102L, 42L, 12L, "REJECTED", "  Evidence did not match.  "),
                        claim(101L, 42L, 11L, "PENDING", null)));

        PartnerRegistrationStatusResponse result = registrationService.registrationStatus(42L);

        assertEquals("MIXED", result.overallStatus());
        assertEquals(List.of("PENDING", "REJECTED", "CANCELLED"), result.properties().stream()
                .map(PartnerRegistrationStatusResponse.PropertyStatus::status)
                .toList());
        assertEquals(List.of(101L, 102L, 103L), result.properties().stream()
                .map(PartnerRegistrationStatusResponse.PropertyStatus::claimId)
                .toList());
        assertEquals(List.of("PENDING", "REJECTED", "CANCELLED"), result.properties().stream()
                .map(PartnerRegistrationStatusResponse.PropertyStatus::claimStatus)
                .toList());
        assertEquals("Evidence did not match.", result.properties().get(1).rejectionReason());
        assertNull(result.properties().get(2).rejectionReason());
    }

    @Test
    void suspendedAndClosedPropertiesExposeOnlyTheirOwnLifecycleReason() {
        UserProperty suspended = ownerMapping(11L, "Suspended", "APPROVED", "SUSPENDED", "ACTIVE");
        suspended.getHotel().setStatus("SUSPENDED");
        suspended.getHotel().setLifecycleReason("  Safety inspection is required.  ");
        UserProperty closed = ownerMapping(12L, "Closed", "APPROVED", "CLOSED", "ACTIVE");
        closed.getHotel().setStatus("CLOSED");
        closed.getHotel().setLifecycleReason("  Property operations ended permanently.  ");
        when(userPropertyRepository.findOwnerMappingsWithHotelByUserId(42L))
                .thenReturn(List.of(suspended, closed));
        when(propertyClaimRequestRepository.findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(
                42L, List.of(11L, 12L)))
                .thenReturn(List.of());

        PartnerRegistrationStatusResponse result = registrationService.registrationStatus(42L);

        assertEquals("Safety inspection is required.", result.properties().get(0).rejectionReason());
        assertEquals("Property operations ended permanently.", result.properties().get(1).rejectionReason());
    }

    @Test
    void inconsistentCombinationFallsBackToCancelledAndNeverApproved() {
        UserProperty mapping = ownerMapping(11L, "Inconsistent", "APPROVED", "INACTIVE", "ACTIVE");
        when(userPropertyRepository.findOwnerMappingsWithHotelByUserId(42L)).thenReturn(List.of(mapping));
        when(propertyClaimRequestRepository.findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(42L, List.of(11L)))
                .thenReturn(List.of());

        PartnerRegistrationStatusResponse result = registrationService.registrationStatus(42L);

        assertEquals("CANCELLED", result.overallStatus());
        assertEquals("CANCELLED", result.properties().getFirst().status());
    }

    @Test
    void duplicateOwnerMappingsCollapseToNewestPropertyRow() {
        UserProperty newest = ownerMapping(11L, "Newest", "APPROVED", "ACTIVE", "ACTIVE");
        newest.setId(20L);
        UserProperty stale = ownerMapping(11L, "Stale", "DRAFT", "INACTIVE", "PENDING");
        stale.setId(10L);
        when(userPropertyRepository.findOwnerMappingsWithHotelByUserId(42L))
                .thenReturn(List.of(newest, stale));
        when(propertyClaimRequestRepository.findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(42L, List.of(11L)))
                .thenReturn(List.of());

        PartnerRegistrationStatusResponse result = registrationService.registrationStatus(42L);

        assertEquals(1, result.propertyCount());
        assertEquals("APPROVED", result.overallStatus());
        assertEquals("Newest", result.properties().getFirst().propertyName());
    }

    private UserProperty ownerMapping(
            Long propertyId,
            String propertyName,
            String approvalStatus,
            String operationStatus,
            String ownershipStatus) {
        Hotel hotel = new Hotel();
        hotel.setId(propertyId);
        hotel.setName(propertyName);
        hotel.setApprovalStatus(approvalStatus);
        hotel.setOperationStatus(operationStatus);

        UserProperty mapping = new UserProperty();
        mapping.setHotel(hotel);
        mapping.setRelationshipType("OWNER");
        mapping.setStatus(ownershipStatus);
        return mapping;
    }

    private PropertyClaimRequest claim(
            Long claimId,
            Long requesterId,
            Long propertyId,
            String status,
            String reason) {
        User requester = new User();
        requester.setId(requesterId);
        Hotel property = new Hotel();
        property.setId(propertyId);
        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setId(claimId);
        claim.setRequesterUser(requester);
        claim.setProperty(property);
        claim.setStatus(status);
        claim.setRejectionReason(reason);
        return claim;
    }
}

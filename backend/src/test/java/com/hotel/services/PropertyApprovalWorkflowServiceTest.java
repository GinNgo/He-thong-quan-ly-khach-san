package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyApprovalWorkflowServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 6, 30);

    @Mock private HotelRepository hotelRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private PropertyOwnershipLifecycleService ownershipLifecycleService;
    @Mock private OperationalAuditService operationalAuditService;
    @Mock private NotificationService notificationService;

    private PropertyApprovalWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        workflowService = new PropertyApprovalWorkflowService(
                hotelRepository,
                userPropertyRepository,
                ownershipLifecycleService,
                operationalAuditService,
                notificationService,
                clock);
    }

    @Test
    void pendingOwnerSubmitsDraftWithDurableActorMetadataAndTenantAudit() {
        Hotel property = property(51L, "DRAFT", "DRAFT", "INACTIVE");
        UserProperty ownership = ownership(property, owner(7L), "PENDING");
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(property));
        when(userPropertyRepository.findPendingOwnerMappingForUpdate(7L, 51L))
                .thenReturn(Optional.of(ownership));
        when(hotelRepository.saveAndFlush(property)).thenReturn(property);

        var result = workflowService.submitDraft(7L, 51L);

        assertEquals("PENDING_APPROVAL", property.getStatus());
        assertEquals("PENDING_APPROVAL", property.getApprovalStatus());
        assertEquals("INACTIVE", property.getOperationStatus());
        assertEquals(7L, property.getSubmittedByUserId());
        assertEquals(NOW, property.getSubmittedAt());
        assertEquals(7L, result.submittedByUserId());
        assertEquals(NOW, result.submittedAt());

        ArgumentCaptor<OperationalAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(operationalAuditService).append(audit.capture());
        assertEquals("TENANT", audit.getValue().scope());
        assertEquals(51L, audit.getValue().hotelId());
        assertEquals(7L, audit.getValue().actorId());
        assertEquals("DRAFT", ((Map<?, ?>) audit.getValue().beforeState()).get("approvalStatus"));
        assertEquals("PENDING_APPROVAL", ((Map<?, ?>) audit.getValue().afterState()).get("approvalStatus"));
    }

    @Test
    void approveActivatesExactPendingOwnerAndPersistsReviewerEvidence() {
        Hotel property = property(51L, "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE");
        property.setSubmittedByUserId(7L);
        property.setSubmittedAt(NOW.minusHours(1));
        User owner = owner(7L);
        UserProperty pending = ownership(property, owner, "PENDING");
        UserProperty active = ownership(property, owner, "ACTIVE");
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(property));
        when(userPropertyRepository.findPendingOwnerMappingsForUpdate(51L)).thenReturn(List.of(pending));
        when(ownershipLifecycleService.activateOwner(51L, 7L)).thenReturn(active);
        when(hotelRepository.saveAndFlush(property)).thenReturn(property);

        var result = workflowService.approve(99L, 51L);

        assertEquals("ACTIVE", result.status());
        assertEquals("APPROVED", result.approvalStatus());
        assertEquals("ACTIVE", result.operationStatus());
        assertEquals("ACTIVE", result.ownershipStatus());
        assertEquals(99L, result.reviewedByUserId());
        assertEquals(NOW, result.reviewedAt());
        assertNull(result.reason());
        verify(ownershipLifecycleService).activateOwner(51L, 7L);
        verify(notificationService).sendUserNotification(
                7L, "PROPERTY_APPROVAL", "Property approved",
                "Your property Harbor Hotel has been approved and is now active.", NOW);

        ArgumentCaptor<OperationalAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(operationalAuditService).append(audit.capture());
        assertEquals("PROPERTY_APPROVED", audit.getValue().eventType());
        assertEquals(99L, audit.getValue().actorId());
        assertEquals("PENDING", ((Map<?, ?>) audit.getValue().beforeState()).get("ownershipStatus"));
        assertEquals("ACTIVE", ((Map<?, ?>) audit.getValue().afterState()).get("ownershipStatus"));
    }

    @Test
    void rejectTrimsReasonDeactivatesExactOwnerAndNotifiesOnlyThatOwner() {
        Hotel property = property(51L, "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE");
        UserProperty pending = ownership(property, owner(7L), "PENDING");
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(property));
        when(userPropertyRepository.findPendingOwnerMappingsForUpdate(51L)).thenReturn(List.of(pending));
        when(ownershipLifecycleService.deactivatePendingOwner(51L, 7L)).thenReturn(true);
        when(hotelRepository.saveAndFlush(property)).thenReturn(property);

        var result = workflowService.reject(99L, 51L, "  Address evidence is incomplete.  ");

        assertEquals("REJECTED", result.status());
        assertEquals("REJECTED", result.approvalStatus());
        assertEquals("INACTIVE", result.operationStatus());
        assertEquals("INACTIVE", result.ownershipStatus());
        assertEquals("Address evidence is incomplete.", result.reason());
        assertEquals("Address evidence is incomplete.", property.getReviewReason());
        verify(ownershipLifecycleService).deactivatePendingOwner(51L, 7L);
        verify(notificationService).sendUserNotification(
                7L, "PROPERTY_APPROVAL", "Property review rejected",
                "Your property Harbor Hotel was rejected. Reason: Address evidence is incomplete.", NOW);
    }

    @Test
    void repeatedOrInconsistentDecisionIsRejectedBeforeOwnerMutation() {
        Hotel approved = property(51L, "ACTIVE", "APPROVED", "ACTIVE");
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(approved));

        assertThrows(IllegalStateException.class, () -> workflowService.approve(99L, 51L));

        verify(userPropertyRepository, never()).findPendingOwnerMappingsForUpdate(any());
        verify(ownershipLifecycleService, never()).activateOwner(any(), any());
        verify(notificationService, never()).sendUserNotification(any(), any(), any(), any(), any());
    }

    @Test
    void ambiguousPendingOwnersFailClosedUntilUniquenessTaskRuns() {
        Hotel property = property(51L, "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE");
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(property));
        when(userPropertyRepository.findPendingOwnerMappingsForUpdate(51L)).thenReturn(List.of(
                ownership(property, owner(7L), "PENDING"),
                ownership(property, owner(8L), "PENDING")));

        assertThrows(IllegalStateException.class, () -> workflowService.approve(99L, 51L));

        verify(ownershipLifecycleService, never()).activateOwner(any(), any());
        verify(hotelRepository, never()).saveAndFlush(any());
    }

    @Test
    void trimmedRejectionReasonMustContainTenToFiveHundredCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> workflowService.reject(99L, 51L, "  too short  "));
        assertThrows(IllegalArgumentException.class,
                () -> workflowService.reject(99L, 51L, "x".repeat(501)));

        verify(hotelRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void missingAuthoritativeReviewerIdIsRejectedBeforeLocks() {
        assertThrows(AccessDeniedException.class, () -> workflowService.approve(null, 51L));
        verify(hotelRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void queueIsTypedAndKeepsLegacySubmissionMetadataNullable() {
        Hotel property = property(51L, "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE");
        UserProperty pending = ownership(property, owner(7L), "PENDING");
        when(userPropertyRepository.findPropertyApprovalQueue()).thenReturn(List.of(pending));

        var queue = workflowService.pendingApprovals();

        assertEquals(1, queue.size());
        assertEquals(51L, queue.getFirst().propertyId());
        assertEquals("Harbor Hotel", queue.getFirst().name());
        assertEquals(7L, queue.getFirst().ownerId());
        assertNull(queue.getFirst().submittedByUserId());
        assertNull(queue.getFirst().submittedAt());
    }

    @Test
    void missingPropertyIsHiddenAsNotFound() {
        when(hotelRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> workflowService.approve(99L, 404L));
    }

    private Hotel property(Long id, String status, String approvalStatus, String operationStatus) {
        Hotel property = new Hotel();
        property.setId(id);
        property.setName("Harbor Hotel");
        property.setNameVi("Harbor Hotel");
        property.setCode("HARBOR");
        property.setAddressLine("12 Test Street");
        property.setPropertyType("HOTEL");
        property.setStatus(status);
        property.setApprovalStatus(approvalStatus);
        property.setOperationStatus(operationStatus);
        return property;
    }

    private User owner(Long id) {
        User owner = new User();
        owner.setId(id);
        owner.setFullName("Owner " + id);
        owner.setEmail("owner" + id + "@example.test");
        return owner;
    }

    private UserProperty ownership(Hotel property, User owner, String status) {
        UserProperty ownership = new UserProperty();
        ownership.setHotel(property);
        ownership.setUser(owner);
        ownership.setRelationshipType("OWNER");
        ownership.setStatus(status);
        return ownership;
    }
}

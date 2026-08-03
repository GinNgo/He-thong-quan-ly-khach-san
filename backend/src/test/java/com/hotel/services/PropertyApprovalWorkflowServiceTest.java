package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyApprovalWorkflowServiceTest {

    @Mock private HotelRepository hotelRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private OperationalAuditService operationalAuditService;

    @InjectMocks
    private PropertyApprovalWorkflowService workflowService;

    @Test
    void pendingOwnerSubmitsDraftAtomicallyWithTenantActorEvidence() {
        Hotel property = draftProperty(51L);
        UserProperty ownership = pendingOwnership(property);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 4, 3, 15);
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(property));
        when(userPropertyRepository.findPendingOwnerMappingForUpdate(7L, 51L))
                .thenReturn(Optional.of(ownership));
        when(hotelRepository.saveAndFlush(property)).thenReturn(property);
        when(operationalAuditService.append(any())).thenReturn(auditEvent(51L, 7L, submittedAt));

        var result = workflowService.submitDraft(7L, 51L);

        assertEquals("PENDING_APPROVAL", property.getStatus());
        assertEquals("PENDING_APPROVAL", property.getApprovalStatus());
        assertEquals("INACTIVE", property.getOperationStatus());
        assertEquals(51L, result.propertyId());
        assertEquals("PENDING_APPROVAL", result.status());
        assertEquals(7L, result.submittedByUserId());
        assertEquals(submittedAt, result.submittedAt());

        ArgumentCaptor<OperationalAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(operationalAuditService).append(audit.capture());
        assertEquals("TENANT", audit.getValue().scope());
        assertEquals(51L, audit.getValue().hotelId());
        assertEquals("PROPERTY_SUBMITTED_FOR_APPROVAL", audit.getValue().eventType());
        assertEquals("USER", audit.getValue().actorType());
        assertEquals(7L, audit.getValue().actorId());
        assertEquals("DRAFT", ((Map<?, ?>) audit.getValue().beforeState()).get("approvalStatus"));
        assertEquals("PENDING_APPROVAL", ((Map<?, ?>) audit.getValue().afterState()).get("approvalStatus"));

        InOrder mutationOrder = inOrder(hotelRepository, userPropertyRepository, operationalAuditService);
        mutationOrder.verify(hotelRepository).findByIdForUpdate(51L);
        mutationOrder.verify(userPropertyRepository).findPendingOwnerMappingForUpdate(7L, 51L);
        mutationOrder.verify(hotelRepository).saveAndFlush(property);
        mutationOrder.verify(operationalAuditService).append(any());
    }

    @Test
    void repeatedSubmissionIsRejectedWithoutAdditionalMutationOrAudit() {
        Hotel property = draftProperty(51L);
        property.setStatus("PENDING_APPROVAL");
        property.setApprovalStatus("PENDING_APPROVAL");
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(property));
        when(userPropertyRepository.findPendingOwnerMappingForUpdate(7L, 51L))
                .thenReturn(Optional.of(pendingOwnership(property)));

        assertThrows(IllegalStateException.class, () -> workflowService.submitDraft(7L, 51L));

        verify(hotelRepository, never()).saveAndFlush(any());
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void inconsistentDraftStateIsRejected() {
        Hotel property = draftProperty(51L);
        property.setOperationStatus("ACTIVE");
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(property));
        when(userPropertyRepository.findPendingOwnerMappingForUpdate(7L, 51L))
                .thenReturn(Optional.of(pendingOwnership(property)));

        assertThrows(IllegalStateException.class, () -> workflowService.submitDraft(7L, 51L));

        verify(hotelRepository, never()).saveAndFlush(any());
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void crossAccountPropertySubmissionIsHiddenAsNotFound() {
        Hotel property = draftProperty(51L);
        when(hotelRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(property));
        when(userPropertyRepository.findPendingOwnerMappingForUpdate(99L, 51L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workflowService.submitDraft(99L, 51L));

        verify(hotelRepository, never()).saveAndFlush(any());
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void missingPropertyDoesNotProbeOwnershipMapping() {
        when(hotelRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workflowService.submitDraft(7L, 404L));

        verify(userPropertyRepository, never()).findPendingOwnerMappingForUpdate(any(), any());
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void missingAuthoritativeAccountIdIsRejectedBeforeLocks() {
        assertThrows(AccessDeniedException.class, () -> workflowService.submitDraft(null, 51L));

        verify(hotelRepository, never()).findByIdForUpdate(any());
    }

    private Hotel draftProperty(Long id) {
        Hotel property = new Hotel();
        property.setId(id);
        property.setName("Harbor Hotel");
        property.setStatus("DRAFT");
        property.setApprovalStatus("DRAFT");
        property.setOperationStatus("INACTIVE");
        return property;
    }

    private UserProperty pendingOwnership(Hotel property) {
        UserProperty ownership = new UserProperty();
        ownership.setHotel(property);
        ownership.setRelationshipType("OWNER");
        ownership.setStatus("PENDING");
        return ownership;
    }

    private OperationalAuditEvent auditEvent(Long propertyId, Long actorId, LocalDateTime occurredAt) {
        return new OperationalAuditEvent(
                "TENANT", propertyId, "PROPERTY", "PROPERTY_SUBMITTED_FOR_APPROVAL",
                "HOTEL", String.valueOf(propertyId), "USER", actorId,
                "Owner submitted property for approval", null, null, "corr", occurredAt);
    }
}

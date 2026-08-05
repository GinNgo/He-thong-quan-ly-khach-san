package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyLifecycleWorkflowServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T01:30:00Z"), ZoneOffset.UTC);

    @Mock private HotelRepository hotelRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private OperationalAuditService operationalAuditService;
    @Mock private NotificationService notificationService;

    private PropertyLifecycleWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new PropertyLifecycleWorkflowService(
                hotelRepository, userPropertyRepository, operationalAuditService,
                notificationService, CLOCK);
    }

    @Test
    void summariesExposeOnlyServerAllowedTransitions() {
        Hotel active = property(1L, "ACTIVE", "APPROVED", "ACTIVE");
        Hotel suspended = property(2L, "SUSPENDED", "APPROVED", "SUSPENDED");
        Hotel closed = property(3L, "CLOSED", "APPROVED", "CLOSED");
        Hotel pending = property(4L, "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE");
        when(hotelRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(active, suspended, closed, pending));

        var summaries = service.properties();

        assertEquals(List.of("SUSPEND", "CLOSE"), summaries.get(0).allowedTransitions());
        assertEquals(List.of("REACTIVATE", "CLOSE"), summaries.get(1).allowedTransitions());
        assertTrue(summaries.get(2).allowedTransitions().isEmpty());
        assertTrue(summaries.get(3).allowedTransitions().isEmpty());
    }

    @Test
    void suspendUpdatesCanonicalStateAuditsAndNotifiesEveryActiveAssignment() {
        Hotel property = property(7L, "ACTIVE", "APPROVED", "ACTIVE");
        when(hotelRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(property));
        when(hotelRepository.saveAndFlush(property)).thenReturn(property);
        when(userPropertyRepository.findActiveAssignedUsersByHotelId(7L))
                .thenReturn(List.of(user(11L), user(12L)));

        var result = service.suspend(99L, 7L, "  Safety inspection is required.  ");

        assertTrue(result.changed());
        assertEquals("SUSPENDED", property.getStatus());
        assertEquals("APPROVED", property.getApprovalStatus());
        assertEquals("SUSPENDED", property.getOperationStatus());
        assertEquals("SUSPEND", property.getLifecycleAction());
        assertEquals("Safety inspection is required.", property.getLifecycleReason());
        assertEquals(99L, property.getLifecycleChangedByUserId());
        verify(notificationService).sendUserNotification(
                11L, "PROPERTY_LIFECYCLE", "Property suspended",
                "Property 7 has been suspended. Reason: Safety inspection is required.",
                property.getLifecycleChangedAt());
        verify(notificationService).sendUserNotification(
                12L, "PROPERTY_LIFECYCLE", "Property suspended",
                "Property 7 has been suspended. Reason: Safety inspection is required.",
                property.getLifecycleChangedAt());

        ArgumentCaptor<OperationalAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(operationalAuditService).append(audit.capture());
        assertEquals("TENANT", audit.getValue().scope());
        assertEquals(7L, audit.getValue().hotelId());
        assertEquals("PROPERTY_SUSPENDED", audit.getValue().eventType());
        assertEquals(99L, audit.getValue().actorId());
    }

    @Test
    void reactivateAndCloseUseCanonicalApprovedStates() {
        Hotel suspended = property(8L, "SUSPENDED", "APPROVED", "SUSPENDED");
        when(hotelRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(suspended));
        when(hotelRepository.saveAndFlush(suspended)).thenReturn(suspended);
        when(userPropertyRepository.findActiveAssignedUsersByHotelId(8L)).thenReturn(List.of());

        var reactivated = service.reactivate(99L, 8L, "Inspection issues were resolved.");

        assertEquals("ACTIVE", reactivated.status());
        assertEquals("APPROVED", reactivated.approvalStatus());
        assertEquals("ACTIVE", reactivated.operationStatus());

        suspended.setStatus("SUSPENDED");
        suspended.setOperationStatus("SUSPENDED");
        suspended.setLifecycleAction(null);
        var closed = service.close(99L, 8L, "Property operations ended permanently.");

        assertEquals("CLOSED", closed.status());
        assertEquals("APPROVED", closed.approvalStatus());
        assertEquals("CLOSED", closed.operationStatus());
    }

    @Test
    void exactReplayIsIdempotentWithoutSecondAuditOrNotification() {
        Hotel suspended = property(9L, "SUSPENDED", "APPROVED", "SUSPENDED");
        suspended.setLifecycleAction("SUSPEND");
        suspended.setLifecycleReason("Safety inspection is required.");
        suspended.setLifecycleChangedByUserId(99L);
        suspended.setLifecycleChangedAt(java.time.LocalDateTime.of(2026, 8, 4, 1, 30));
        when(hotelRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(suspended));

        var result = service.suspend(99L, 9L, "Safety inspection is required.");

        assertFalse(result.changed());
        verify(hotelRepository, never()).saveAndFlush(any());
        verifyNoInteractions(operationalAuditService, notificationService);
        verify(userPropertyRepository, never()).findActiveAssignedUsersByHotelId(any());
    }

    @Test
    void replayWithDifferentActorOrReasonIsRejectedAsAStateConflict() {
        Hotel suspended = property(9L, "SUSPENDED", "APPROVED", "SUSPENDED");
        suspended.setLifecycleAction("SUSPEND");
        suspended.setLifecycleReason("Safety inspection is required.");
        suspended.setLifecycleChangedByUserId(99L);
        when(hotelRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(suspended));

        assertThrows(IllegalStateException.class,
                () -> service.suspend(100L, 9L, "Safety inspection is required."));
        assertThrows(IllegalStateException.class,
                () -> service.suspend(99L, 9L, "A different lifecycle reason was supplied."));

        verify(hotelRepository, never()).saveAndFlush(any());
        verifyNoInteractions(operationalAuditService, notificationService);
    }

    @Test
    void invalidOrTerminalTransitionsFailClosed() {
        Hotel pending = property(10L, "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pending));
        assertThrows(IllegalStateException.class,
                () -> service.suspend(99L, 10L, "Safety inspection is required."));

        Hotel closed = property(11L, "CLOSED", "APPROVED", "CLOSED");
        closed.setLifecycleAction("CLOSE");
        when(hotelRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(closed));
        assertThrows(IllegalStateException.class,
                () -> service.reactivate(99L, 11L, "Reactivation was requested by mistake."));
    }

    @Test
    void reasonLengthIsValidatedBeforeLocking() {
        assertThrows(IllegalArgumentException.class,
                () -> service.suspend(99L, 7L, " short "));
        assertThrows(IllegalArgumentException.class,
                () -> service.close(99L, 7L, "x".repeat(501)));
        verifyNoInteractions(hotelRepository);
    }

    @Test
    void auditOrNotificationFailurePropagatesForTransactionRollback() {
        Hotel auditProperty = property(12L, "ACTIVE", "APPROVED", "ACTIVE");
        when(hotelRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(auditProperty));
        when(hotelRepository.saveAndFlush(auditProperty)).thenReturn(auditProperty);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(operationalAuditService).append(any());

        assertThrows(IllegalStateException.class,
                () -> service.suspend(99L, 12L, "Safety inspection is required."));
        verifyNoInteractions(notificationService);
    }

    private Hotel property(Long id, String status, String approval, String operation) {
        Hotel property = new Hotel();
        property.setId(id);
        property.setName("Property " + id);
        property.setNameVi("Property " + id);
        property.setCode("P-" + id);
        property.setAddressLine(id + " Test Street");
        property.setPropertyType("HOTEL");
        property.setStatus(status);
        property.setApprovalStatus(approval);
        property.setOperationStatus(operation);
        return property;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.test");
        return user;
    }
}

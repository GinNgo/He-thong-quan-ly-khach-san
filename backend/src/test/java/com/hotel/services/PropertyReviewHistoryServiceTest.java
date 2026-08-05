package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.OperationalAuditEventRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyReviewHistoryServiceTest {

    @Mock private OperationalAuditEventRepository auditRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private HotelRepository hotelRepository;

    private PropertyReviewHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PropertyReviewHistoryService(
                auditRepository, userPropertyRepository, hotelRepository, new ObjectMapper());
    }

    @Test
    void ownerHistoryAcceptsAnyLegacyOwnerMappingAndReturnsOnlySafeProjection() {
        when(userPropertyRepository.existsByUserIdAndHotelIdAndRelationshipType(7L, 51L, "OWNER"))
                .thenReturn(true);
        OperationalAuditEvent event = event(
                "PROPERTY_REJECTED",
                "Address evidence is incomplete.",
                "{\"status\":\"PENDING_APPROVAL\",\"approvalStatus\":\"PENDING_APPROVAL\",\"operationStatus\":\"INACTIVE\",\"ownershipStatus\":\"PENDING\"}",
                "{\"status\":\"REJECTED\",\"approvalStatus\":\"REJECTED\",\"operationStatus\":\"INACTIVE\",\"ownershipStatus\":\"INACTIVE\"}");
        when(auditRepository.findPropertyTransitionHistory(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(event)));

        var history = service.ownerHistory(7L, 51L);

        assertEquals(1, history.size());
        assertEquals("ADMIN", history.getFirst().actorKind());
        assertEquals("PENDING", history.getFirst().beforeState().ownershipStatus());
        assertEquals("INACTIVE", history.getFirst().afterState().ownershipStatus());
        assertEquals("Address evidence is incomplete.", history.getFirst().note());
    }

    @Test
    void submittedEventIsOwnerAuthoredAndMalformedLegacyStateIsSafelyNull() {
        when(userPropertyRepository.existsByUserIdAndHotelIdAndRelationshipType(7L, 51L, "OWNER"))
                .thenReturn(true);
        OperationalAuditEvent event = event(
                "PROPERTY_SUBMITTED_FOR_APPROVAL",
                "Owner submitted property for approval",
                "legacy-not-json",
                null);
        when(auditRepository.findPropertyTransitionHistory(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(event)));

        var item = service.ownerHistory(7L, 51L).getFirst();

        assertEquals("OWNER", item.actorKind());
        assertNull(item.beforeState());
        assertNull(item.afterState());
    }

    @Test
    void ownerHistoryHidesCrossAccountPropertyAsNotFound() {
        when(userPropertyRepository.existsByUserIdAndHotelIdAndRelationshipType(8L, 51L, "OWNER"))
                .thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.ownerHistory(8L, 51L));
    }

    @Test
    void adminHistoryRequiresExistingPropertyAndCapsQueryAtLatestHundred() {
        when(hotelRepository.existsById(51L)).thenReturn(true);
        when(auditRepository.findPropertyTransitionHistory(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.adminHistory(51L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findPropertyTransitionHistory(
                eq(51L), eq("51"), any(),
                pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());
        Sort.Order occurredAt = pageable.getSort().getOrderFor("occurredAt");
        Sort.Order id = pageable.getSort().getOrderFor("id");
        assertNotNull(occurredAt);
        assertNotNull(id);
        assertEquals(Sort.Direction.DESC, occurredAt.getDirection());
        assertEquals(Sort.Direction.DESC, id.getDirection());
        assertEquals(List.of("occurredAt", "id"), pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .toList());
    }

    private OperationalAuditEvent event(
            String eventType,
            String reason,
            String beforeState,
            String afterState) {
        OperationalAuditEvent event = mock(OperationalAuditEvent.class);
        when(event.getId()).thenReturn(101L);
        when(event.getHotelId()).thenReturn(51L);
        when(event.getEventType()).thenReturn(eventType);
        when(event.getReason()).thenReturn(reason);
        when(event.getBeforeStateJson()).thenReturn(beforeState);
        when(event.getAfterStateJson()).thenReturn(afterState);
        when(event.getOccurredAt()).thenReturn(LocalDateTime.of(2026, 8, 4, 8, 0));
        return event;
    }
}

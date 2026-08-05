package com.hotel.services;

import com.hotel.entities.SupportConversation;
import com.hotel.entities.SupportConversationEvent;
import com.hotel.repositories.SupportConversationEventRepository;
import com.hotel.repositories.SupportConversationRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportConversationAuditServiceTest {
    @Mock SupportConversationEventRepository eventRepository;
    @Mock UserRepository userRepository;
    @Mock SupportConversationRepository conversationRepository;
    @Mock PropertyAccessService propertyAccessService;

    @Test
    void authorizedSupervisorReadsPagedImmutableLifecycleHistory() {
        SupportConversation conversation = new SupportConversation(); conversation.setId(9L); conversation.setHotelId(7L);
        SupportConversationEvent event = new SupportConversationEvent(); event.setConversation(conversation); event.setHotelId(7L); event.setEventType("CLOSED"); event.setDetails("Resolved"); event.setOccurredAt(Instant.parse("2026-08-04T10:00:00Z"));
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(eventRepository.findByConversationIdAndOccurredAtGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(9L), any(), any())).thenReturn(new PageImpl<>(List.of(event)));
        SupportConversationAuditService service = new SupportConversationAuditService(eventRepository, userRepository, conversationRepository, propertyAccessService);

        var page = service.history(9L, 0, 25);

        assertEquals("CLOSED", page.getContent().getFirst().eventType());
        assertEquals(730, service.policy().get("retentionDays"));
    }

    @Test
    void foreignConversationIsDeniedBeforeEventQuery() {
        SupportConversation conversation = new SupportConversation(); conversation.setId(99L); conversation.setHotelId(8L);
        when(conversationRepository.findById(99L)).thenReturn(Optional.of(conversation));
        org.mockito.Mockito.doThrow(new RuntimeException("not found")).when(propertyAccessService).requireAccessibleOrNotFound(8L, "support conversation");
        SupportConversationAuditService service = new SupportConversationAuditService(eventRepository, userRepository, conversationRepository, propertyAccessService);
        assertThrows(RuntimeException.class, () -> service.history(99L, 0, 25));
        verifyNoInteractions(eventRepository);
    }
}

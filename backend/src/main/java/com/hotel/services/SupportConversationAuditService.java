package com.hotel.services;

import com.hotel.entities.SupportConversation;
import com.hotel.entities.SupportConversationEvent;
import com.hotel.repositories.SupportConversationEventRepository;
<<<<<<< HEAD
import com.hotel.repositories.SupportConversationRepository;
import com.hotel.repositories.UserRepository;
=======
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
>>>>>>> codex/ui-functional-audit-polish
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
<<<<<<< HEAD
public class SupportConversationAuditService {
    private static final int RETENTION_DAYS = 730;
    private final SupportConversationEventRepository eventRepository;
    private final UserRepository userRepository;
    private final SupportConversationRepository conversationRepository;
    private final PropertyAccessService propertyAccessService;

    public SupportConversationAuditService(
            SupportConversationEventRepository eventRepository,
            UserRepository userRepository,
            SupportConversationRepository conversationRepository,
            PropertyAccessService propertyAccessService) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.propertyAccessService = propertyAccessService;
    }
=======
@RequiredArgsConstructor
public class SupportConversationAuditService {

    private final SupportConversationEventRepository eventRepository;
    private final UserRepository userRepository;
>>>>>>> codex/ui-functional-audit-polish

    @Transactional
    public void record(SupportConversation conversation, Long actorUserId, String eventType, String details) {
        save(conversation, actorUserId, eventType, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(SupportConversation conversation, Long actorUserId, String eventType, String details) {
        save(conversation, actorUserId, eventType, details);
    }

    private void save(SupportConversation conversation, Long actorUserId, String eventType, String details) {
        SupportConversationEvent event = new SupportConversationEvent();
        event.setConversation(conversation);
<<<<<<< HEAD
        event.setHotelId(conversation.getHotelId());
        if (actorUserId != null) userRepository.findById(actorUserId).ifPresent(event::setActor);
=======
        event.setHotel(conversation.getHotel());
        if (actorUserId != null) {
            userRepository.findById(actorUserId).ifPresent(event::setActor);
        }
>>>>>>> codex/ui-functional-audit-polish
        event.setEventType(eventType);
        event.setDetails(details);
        event.setOccurredAt(Instant.now());
        eventRepository.save(event);
    }
<<<<<<< HEAD

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.hotel.dtos.SupportConversationEventDTO> history(
            Long conversationId, int page, int size) {
        SupportConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Support conversation not found."));
        if (conversation.getHotelId() == null) {
            if (!propertyAccessService.isSystemAdministrator()) throw new com.hotel.exceptions.ResourceNotFoundException("Support conversation not found.");
        } else {
            propertyAccessService.requireAccessibleOrNotFound(conversation.getHotelId(), "support conversation");
        }
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("occurredAt"), org.springframework.data.domain.Sort.Order.desc("id")));
        Instant retentionCutoff = Instant.now().minus(RETENTION_DAYS, java.time.temporal.ChronoUnit.DAYS);
        return eventRepository.findByConversationIdAndOccurredAtGreaterThanEqual(
                conversationId, retentionCutoff, pageable).map(event ->
                new com.hotel.dtos.SupportConversationEventDTO(event.getId(), conversationId, event.getHotelId(),
                        event.getActor() == null ? null : event.getActor().getId(), event.getEventType(), event.getDetails(), event.getOccurredAt()));
    }

    public java.util.Map<String, Object> policy() {
        return java.util.Map.of("appendOnly", true, "retentionDays", RETENTION_DAYS, "pageMaxRows", 100,
                "events", java.util.List.of("CREATED", "ASSIGNED", "UNASSIGNED", "ESCALATED", "CLOSED", "REOPENED", "MESSAGE_DELIVERED", "MESSAGE_READ"));
    }
=======
>>>>>>> codex/ui-functional-audit-polish
}

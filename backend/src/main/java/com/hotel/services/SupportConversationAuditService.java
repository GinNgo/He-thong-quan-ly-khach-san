package com.hotel.services;

import com.hotel.entities.SupportConversation;
import com.hotel.entities.SupportConversationEvent;
import com.hotel.repositories.SupportConversationEventRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SupportConversationAuditService {

    private final SupportConversationEventRepository eventRepository;
    private final UserRepository userRepository;

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
        event.setHotel(conversation.getHotel());
        if (actorUserId != null) {
            userRepository.findById(actorUserId).ifPresent(event::setActor);
        }
        event.setEventType(eventType);
        event.setDetails(details);
        event.setOccurredAt(Instant.now());
        eventRepository.save(event);
    }
}

package com.hotel.repositories;

import com.hotel.entities.SupportConversationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportConversationEventRepository extends JpaRepository<SupportConversationEvent, Long> {
    long countByConversationIdAndEventType(Long conversationId, String eventType);
}

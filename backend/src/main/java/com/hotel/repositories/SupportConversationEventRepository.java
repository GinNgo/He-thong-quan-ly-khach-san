package com.hotel.repositories;

import com.hotel.entities.SupportConversationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface SupportConversationEventRepository extends JpaRepository<SupportConversationEvent, Long> {
    long countByConversationIdAndEventType(Long conversationId, String eventType);
    Page<SupportConversationEvent> findByConversationIdAndOccurredAtGreaterThanEqual(
            Long conversationId, Instant retentionCutoff, Pageable pageable);
}

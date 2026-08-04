package com.hotel.repositories;

import com.hotel.entities.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    long countByReceiverIdAndIsReadFalse(Long receiverId);

    boolean existsBySenderIdAndReceiverId(Long senderId, Long receiverId);

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE (m.senderId = :customerId AND m.receiverId = 0)
               OR m.receiverId = :customerId
            ORDER BY m.timestamp ASC
            """)
    List<ChatMessage> findCustomerSupportHistory(@Param("customerId") Long customerId);

    @Query("SELECT DISTINCT m.senderId FROM ChatMessage m WHERE m.receiverId = 0")
    List<Long> findDistinctQueueCustomerIds();

    Page<ChatMessage> findByConversationIdAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
            Long conversationId, Instant cutoff, Pageable pageable);

    Optional<ChatMessage> findFirstByConversationIdOrderByTimestampDesc(Long conversationId);
}

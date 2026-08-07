package com.hotel.repositories;

import com.hotel.entities.ChatMessage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    long countByReceiverIdAndIsReadFalse(Long receiverId);

<<<<<<< HEAD
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

    Optional<ChatMessage> findByConversationIdAndSenderIdAndClientMessageId(
            Long conversationId, Long senderId, String clientMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from ChatMessage message where message.id = :id")
    Optional<ChatMessage> findLockedById(@Param("id") Long id);

=======
>>>>>>> codex/ui-functional-audit-polish
    List<ChatMessage> findByConversationIdAndLegacyUnscopedFalseOrderByTimestampAsc(Long conversationId);
}

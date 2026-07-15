package com.hotel.repositories;

import com.hotel.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    long countByReceiverIdAndIsReadFalse(Long receiverId);
    
    @Query("SELECT m FROM ChatMessage m WHERE (m.senderId = :userId1 AND m.receiverId = :userId2) OR (m.senderId = :userId2 AND m.receiverId = :userId1) ORDER BY m.timestamp ASC")
    List<ChatMessage> findChatHistory(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    // Find all distinct users that have chatted with Admin (receiverId = 0 or Admin's ID)
    @Query("SELECT DISTINCT m.senderId FROM ChatMessage m WHERE m.receiverId = :adminId UNION SELECT DISTINCT m.receiverId FROM ChatMessage m WHERE m.senderId = :adminId")
    List<Long> findDistinctUsersChattedWith(@Param("adminId") Long adminId);
}

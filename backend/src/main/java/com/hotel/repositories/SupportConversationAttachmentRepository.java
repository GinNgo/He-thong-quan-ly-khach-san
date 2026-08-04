package com.hotel.repositories;

import com.hotel.entities.SupportConversationAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportConversationAttachmentRepository
        extends JpaRepository<SupportConversationAttachment, Long> {
    List<SupportConversationAttachment> findByConversationIdOrderByUploadedAtAscIdAsc(Long conversationId);
}

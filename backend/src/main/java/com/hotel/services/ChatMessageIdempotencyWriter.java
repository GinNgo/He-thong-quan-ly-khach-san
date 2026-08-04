package com.hotel.services;

import com.hotel.entities.ChatMessage;
import com.hotel.entities.SupportConversation;
import com.hotel.exceptions.ChatMessageConflictException;
import com.hotel.repositories.ChatMessageRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class ChatMessageIdempotencyWriter {

    private final ChatMessageRepository messageRepository;
    private final JdbcTemplate jdbcTemplate;

    public ChatMessageIdempotencyWriter(
            ChatMessageRepository messageRepository,
            JdbcTemplate jdbcTemplate) {
        this.messageRepository = messageRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WriteResult createOrLoad(
            SupportConversation conversation,
            Long senderId,
            Long receiverId,
            String clientMessageId,
            String content) {
        ChatMessage existing = messageRepository
                .findByConversationIdAndSenderIdAndClientMessageId(
                        conversation.getId(), senderId, clientMessageId)
                .orElse(null);
        if (existing != null) return replay(existing, content);

        Instant createdAt = Instant.now();
        int inserted = merge(conversation, senderId, receiverId, clientMessageId, content, createdAt);
        ChatMessage saved = messageRepository
                .findByConversationIdAndSenderIdAndClientMessageId(
                        conversation.getId(), senderId, clientMessageId)
                .orElseThrow(() -> new IllegalStateException("Chat idempotency row was not persisted."));
        if (!saved.getContent().equals(content)) throw new ChatMessageConflictException();
        return new WriteResult(saved, inserted > 0);
    }

    private WriteResult replay(ChatMessage existing, String content) {
        if (!existing.getContent().equals(content)) throw new ChatMessageConflictException();
        return new WriteResult(existing, false);
    }

    private int merge(
            SupportConversation conversation,
            Long senderId,
            Long receiverId,
            String clientMessageId,
            String content,
            Instant createdAt) {
        String product = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
        String target = product != null && product.toLowerCase().contains("microsoft sql server")
                ? "dbo.chat_messages WITH (HOLDLOCK)" : "chat_messages";
        String sql = """
                MERGE INTO %s AS target
                USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source
                    (conversation_id, hotel_id, legacy_unscoped, sender_id, receiver_id,
                     client_message_id, content, message_time, is_read, delivery_status)
                ON target.conversation_id = source.conversation_id
                   AND target.sender_id = source.sender_id
                   AND target.client_message_id = source.client_message_id
                WHEN NOT MATCHED THEN
                    INSERT (conversation_id, hotel_id, legacy_unscoped, sender_id, receiver_id,
                            client_message_id, content, timestamp, is_read, delivery_status)
                    VALUES (source.conversation_id, source.hotel_id, source.legacy_unscoped,
                            source.sender_id, source.receiver_id, source.client_message_id,
                            source.content, source.message_time, source.is_read, source.delivery_status)
                """.formatted(target);
        return jdbcTemplate.update(sql,
                conversation.getId(), conversation.getHotelId(), conversation.getHotelId() == null,
                senderId, receiverId, clientMessageId, content, Timestamp.from(createdAt), false, "PERSISTED");
    }

    public record WriteResult(ChatMessage message, boolean created) {
    }
}

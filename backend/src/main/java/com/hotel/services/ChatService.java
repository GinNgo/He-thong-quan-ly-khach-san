package com.hotel.services;

import com.hotel.dtos.ChatMessageDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.repositories.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessageDTO saveMessage(ChatMessageDTO dto) {
        ChatMessage entity = new ChatMessage();
        entity.setSenderId(dto.getSenderId());
        entity.setReceiverId(dto.getReceiverId());
        entity.setContent(dto.getContent());
        
        ChatMessage saved = chatMessageRepository.save(entity);
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getChatHistory(Long userId1, Long userId2) {
        return chatMessageRepository.findChatHistory(userId1, userId2)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getActiveChatUsers(Long adminId) {
        return chatMessageRepository.findDistinctUsersChattedWith(adminId);
    }

    private ChatMessageDTO mapToDTO(ChatMessage entity) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(entity.getId());
        dto.setSenderId(entity.getSenderId());
        dto.setReceiverId(entity.getReceiverId());
        dto.setContent(entity.getContent());
        dto.setTimestamp(entity.getTimestamp());
        dto.setRead(entity.isRead());
        return dto;
    }
}

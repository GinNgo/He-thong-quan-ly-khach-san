package com.hotel.services;

import com.hotel.dtos.ChatMessageDTO;
import com.hotel.dtos.ChatConversationDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.ChatMessageRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatAuthorizationService authorizationService;

    @Transactional
    public ChatMessageDTO sendToSupport(CustomUserDetails sender, String content) {
        return saveMessage(sender.getUserId(), 0L, normalizeContent(content));
    }

    @Transactional
    public ChatMessageDTO replyToCustomer(CustomUserDetails support, Long customerId, String content) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        ensureQueueConversation(customerId);
        userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hội thoại hỗ trợ."));
        return saveMessage(support.getUserId(), customerId, normalizeContent(content));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMyHistory(CustomUserDetails userDetails) {
        return mapHistory(userDetails.getUserId());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getSupportHistory(CustomUserDetails support, Long customerId) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        ensureQueueConversation(customerId);
        return mapHistory(customerId);
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDTO> getSupportConversations(CustomUserDetails support) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        return chatMessageRepository.findDistinctQueueCustomerIds().stream()
                .map(this::toConversation)
                .sorted(Comparator.comparing(
                        ChatConversationDTO::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getUsername(Long userId) {
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hội thoại hỗ trợ."));
    }

    private ChatMessageDTO saveMessage(Long senderId, Long receiverId, String content) {
        ChatMessage entity = new ChatMessage();
        entity.setSenderId(senderId);
        entity.setReceiverId(receiverId);
        entity.setContent(content);
        return mapToDTO(chatMessageRepository.save(entity));
    }

    private List<ChatMessageDTO> mapHistory(Long customerId) {
        return chatMessageRepository.findCustomerSupportHistory(customerId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private ChatConversationDTO toConversation(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hội thoại hỗ trợ."));
        List<ChatMessage> history = chatMessageRepository.findCustomerSupportHistory(customerId);
        ChatMessage lastMessage = history.isEmpty() ? null : history.get(history.size() - 1);
        String customerName = customer.getFullName() == null || customer.getFullName().isBlank()
                ? customer.getUsername()
                : customer.getFullName();
        return new ChatConversationDTO(
                customerId,
                customerName,
                lastMessage == null ? "" : lastMessage.getContent(),
                lastMessage == null ? Instant.EPOCH : lastMessage.getTimestamp());
    }

    private void ensureQueueConversation(Long customerId) {
        if (customerId == null || !chatMessageRepository.existsBySenderIdAndReceiverId(customerId, 0L)) {
            throw new ResourceNotFoundException("Không tìm thấy hội thoại hỗ trợ.");
        }
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống.");
        }
        String normalized = content.trim();
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được vượt quá 2.000 ký tự.");
        }
        return normalized;
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

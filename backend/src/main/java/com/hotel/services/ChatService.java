package com.hotel.services;

import com.hotel.dtos.ChatConversationDTO;
import com.hotel.dtos.ChatMessageDTO;
import com.hotel.dtos.ChatPageDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.entities.SupportConversation;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.ChatMessageRepository;
import com.hotel.repositories.SupportConversationRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final SupportConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ChatAuthorizationService authorizationService;
    private final int retentionDays;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            SupportConversationRepository conversationRepository,
            UserRepository userRepository,
            ChatAuthorizationService authorizationService,
            @Value("${app.chat.retention-days:365}") int retentionDays) {
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.retentionDays = Math.min(Math.max(retentionDays, 30), 3650);
    }

    @Transactional
    public ChatConversationDTO createConversation(CustomUserDetails customer, String subject) {
        SupportConversation conversation = new SupportConversation();
        conversation.setCustomerId(customer.getUserId());
        conversation.setSubject(normalizeSubject(subject));
        return toConversation(conversationRepository.save(conversation));
    }

    @Transactional
    public ChatMessageDTO sendToSupport(CustomUserDetails sender, Long conversationId, String content) {
        SupportConversation conversation = conversationId == null
                ? conversationRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(sender.getUserId())
                        .orElseGet(() -> createEntity(sender.getUserId(), "Ho tro chung"))
                : requireOwnedConversation(conversationId, sender.getUserId());
        ChatMessageDTO result = saveMessage(
                conversation.getId(), sender.getUserId(), 0L, normalizeContent(content));
        touch(conversation);
        return result;
    }

    @Transactional
    public ChatMessageDTO sendToSupport(CustomUserDetails sender, String content) {
        return sendToSupport(sender, null, content);
    }

    @Transactional
    public ChatMessageDTO replyToConversation(
            CustomUserDetails support, Long conversationId, String content) {
        authorizationService.requirePermission(support, ActionCode.CREATE);
        SupportConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
        userRepository.findById(conversation.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
        ChatMessageDTO result = saveMessage(
                conversation.getId(), support.getUserId(), conversation.getCustomerId(), normalizeContent(content));
        touch(conversation);
        return result;
    }

    @Transactional
    public ChatMessageDTO replyToCustomer(CustomUserDetails support, Long customerId, String content) {
        SupportConversation conversation = conversationRepository
                .findFirstByCustomerIdOrderByUpdatedAtDesc(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
        return replyToConversation(support, conversation.getId(), content);
    }

    @Transactional(readOnly = true)
    public ChatPageDTO<ChatConversationDTO> getMyConversations(
            CustomUserDetails customer, int page, int size) {
        Page<SupportConversation> result = conversationRepository
                .findByCustomerIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
                        customer.getUserId(), cutoff(), safePage(page, size));
        return page(result.map(this::toConversation));
    }

    @Transactional(readOnly = true)
    public ChatPageDTO<ChatMessageDTO> getMyConversationMessages(
            CustomUserDetails customer, Long conversationId, int page, int size) {
        requireOwnedConversation(conversationId, customer.getUserId());
        return messagePage(conversationId, page, size);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMyHistory(CustomUserDetails userDetails) {
        return conversationRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(userDetails.getUserId())
                .map(conversation -> messagePage(conversation.getId(), 0, 100).content())
                .orElseGet(() -> mapLegacyHistory(userDetails.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getSupportHistory(CustomUserDetails support, Long conversationId) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
        return messagePage(conversationId, 0, 100).content();
    }

    @Transactional(readOnly = true)
    public ChatPageDTO<ChatMessageDTO> getSupportConversationMessages(
            CustomUserDetails support, Long conversationId, int page, int size) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
        return messagePage(conversationId, page, size);
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDTO> getSupportConversations(CustomUserDetails support) {
        authorizationService.requirePermission(support, ActionCode.VIEW);
        return conversationRepository.findByUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
                        cutoff(), PageRequest.of(0, 100))
                .stream()
                .map(this::toConversation)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getUsername(Long userId) {
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
    }

    @Transactional(readOnly = true)
    public Long getConversationCustomerId(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .map(SupportConversation::getCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
    }

    private SupportConversation createEntity(Long customerId, String subject) {
        SupportConversation conversation = new SupportConversation();
        conversation.setCustomerId(customerId);
        conversation.setSubject(subject);
        return conversationRepository.save(conversation);
    }

    private SupportConversation requireOwnedConversation(Long conversationId, Long customerId) {
        return conversationRepository.findByIdAndCustomerId(conversationId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
    }

    private ChatMessageDTO saveMessage(
            Long conversationId, Long senderId, Long receiverId, String content) {
        ChatMessage entity = new ChatMessage();
        entity.setConversationId(conversationId);
        entity.setSenderId(senderId);
        entity.setReceiverId(receiverId);
        entity.setContent(content);
        return mapToDTO(chatMessageRepository.save(entity));
    }

    private ChatPageDTO<ChatMessageDTO> messagePage(Long conversationId, int page, int size) {
        Page<ChatMessage> result = chatMessageRepository
                .findByConversationIdAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
                        conversationId, cutoff(), safePage(page, size));
        List<ChatMessageDTO> chronological = new ArrayList<>(result.stream().map(this::mapToDTO).toList());
        chronological.sort(Comparator.comparing(ChatMessageDTO::getTimestamp));
        return new ChatPageDTO<>(chronological, result.getTotalElements(), result.getTotalPages(),
                result.getNumber(), result.getSize(), result.isFirst(), result.isLast(), retentionDays);
    }

    private List<ChatMessageDTO> mapLegacyHistory(Long customerId) {
        return chatMessageRepository.findCustomerSupportHistory(customerId).stream()
                .filter(message -> message.getTimestamp() == null || !message.getTimestamp().isBefore(cutoff()))
                .map(this::mapToDTO)
                .toList();
    }

    private ChatConversationDTO toConversation(SupportConversation conversation) {
        User customer = userRepository.findById(conversation.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found."));
        ChatMessage lastMessage = chatMessageRepository
                .findFirstByConversationIdOrderByTimestampDesc(conversation.getId()).orElse(null);
        String customerName = customer.getFullName() == null || customer.getFullName().isBlank()
                ? customer.getUsername() : customer.getFullName();
        return new ChatConversationDTO(
                conversation.getId(),
                conversation.getCustomerId(),
                customerName,
                conversation.getSubject(),
                lastMessage == null ? "" : lastMessage.getContent(),
                lastMessage == null ? conversation.getUpdatedAt() : lastMessage.getTimestamp());
    }

    private ChatMessageDTO mapToDTO(ChatMessage entity) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(entity.getId());
        dto.setConversationId(entity.getConversationId());
        dto.setSenderId(entity.getSenderId());
        dto.setReceiverId(entity.getReceiverId());
        dto.setContent(entity.getContent());
        dto.setTimestamp(entity.getTimestamp());
        dto.setRead(entity.isRead());
        return dto;
    }

    private PageRequest safePage(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private ChatPageDTO<ChatConversationDTO> page(Page<ChatConversationDTO> page) {
        return new ChatPageDTO<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize(), page.isFirst(), page.isLast(), retentionDays);
    }

    private Instant cutoff() { return Instant.now().minus(retentionDays, ChronoUnit.DAYS); }

    private void touch(SupportConversation conversation) {
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    private String normalizeSubject(String subject) {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Conversation subject is required.");
        String normalized = subject.trim();
        if (normalized.length() > 120) throw new IllegalArgumentException("Conversation subject exceeds 120 characters.");
        return normalized;
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message content is required.");
        String normalized = content.trim();
        if (normalized.length() > 2000) throw new IllegalArgumentException("Message content exceeds 2000 characters.");
        return normalized;
    }
}

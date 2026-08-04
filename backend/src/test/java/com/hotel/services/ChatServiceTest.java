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
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private SupportConversationRepository conversationRepository;
    @Mock private UserRepository userRepository;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatMessageRepository,
                conversationRepository,
                userRepository,
                new ChatAuthorizationService(),
                90);
    }

    @Test
    void firstCustomerMessageCreatesAConversationAndUsesAuthenticatedSender() {
        CustomUserDetails customer = user(42L, Map.of());
        when(conversationRepository.findFirstByCustomerIdOrderByUpdatedAtDesc(42L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(SupportConversation.class))).thenAnswer(invocation -> {
            SupportConversation conversation = invocation.getArgument(0);
            conversation.setId(9L);
            conversation.setCreatedAt(Instant.parse("2026-08-04T09:00:00Z"));
            conversation.setUpdatedAt(Instant.parse("2026-08-04T09:00:00Z"));
            return conversation;
        });
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> savedMessage(
                invocation.getArgument(0), 99L, Instant.parse("2026-08-04T10:00:00Z")));

        ChatMessageDTO result = chatService.sendToSupport(customer, "  Xin chao  ");

        assertEquals(9L, result.getConversationId());
        assertEquals(42L, result.getSenderId());
        assertEquals(0L, result.getReceiverId());
        assertEquals("Xin chao", result.getContent());
    }

    @Test
    void customerCannotReadOrSendToAnotherCustomersConversation() {
        CustomUserDetails customer = user(42L, Map.of());
        when(conversationRepository.findByIdAndCustomerId(91L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> chatService.getMyConversationMessages(customer, 91L, 0, 20));
        assertThrows(ResourceNotFoundException.class,
                () -> chatService.sendToSupport(customer, 91L, "cross account"));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void customerCanSelectMultipleOwnConversations() {
        CustomUserDetails customer = user(42L, Map.of());
        SupportConversation newest = conversation(12L, 42L, "Hoa don", "2026-08-04T10:00:00Z");
        SupportConversation older = conversation(11L, 42L, "Dat phong", "2026-08-04T09:00:00Z");
        PageRequest request = PageRequest.of(0, 20);
        when(conversationRepository.findByCustomerIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
                eq(42L), any(Instant.class), eq(request)))
                .thenReturn(new PageImpl<>(List.of(newest, older), request, 2));
        when(userRepository.findById(42L)).thenReturn(Optional.of(customerEntity(42L)));
        when(chatMessageRepository.findFirstByConversationIdOrderByTimestampDesc(any()))
                .thenReturn(Optional.empty());

        ChatPageDTO<ChatConversationDTO> page = chatService.getMyConversations(customer, 0, 20);

        assertEquals(List.of(12L, 11L), page.content().stream()
                .map(ChatConversationDTO::getConversationId).toList());
        assertEquals(90, page.retentionDays());
    }

    @Test
    void messagePagesAreChronologicalAndExposeRetention() {
        CustomUserDetails customer = user(42L, Map.of());
        SupportConversation conversation = conversation(9L, 42L, "Dat phong", "2026-08-04T10:00:00Z");
        ChatMessage newer = message(2L, 9L, 7L, 42L, "reply", "2026-08-04T10:02:00Z");
        ChatMessage older = message(1L, 9L, 42L, 0L, "question", "2026-08-04T10:01:00Z");
        PageRequest request = PageRequest.of(1, 2);
        when(conversationRepository.findByIdAndCustomerId(9L, 42L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationIdAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
                eq(9L), any(Instant.class), eq(request)))
                .thenReturn(new PageImpl<>(List.of(newer, older), request, 4));
        Instant earliestExpectedCutoff = Instant.now().minus(90, ChronoUnit.DAYS).minusSeconds(2);

        ChatPageDTO<ChatMessageDTO> page = chatService.getMyConversationMessages(customer, 9L, 1, 2);

        assertEquals(List.of("question", "reply"), page.content().stream()
                .map(ChatMessageDTO::getContent).toList());
        assertEquals(4, page.totalElements());
        assertEquals(1, page.number());
        assertEquals(90, page.retentionDays());
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(chatMessageRepository).findByConversationIdAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
                eq(9L), cutoff.capture(), eq(request));
        org.junit.jupiter.api.Assertions.assertTrue(cutoff.getValue().isAfter(earliestExpectedCutoff));
        org.junit.jupiter.api.Assertions.assertTrue(
                cutoff.getValue().isBefore(Instant.now().minus(90, ChronoUnit.DAYS).plusSeconds(2)));
    }

    @Test
    void supportReplyRequiresCreatePermission() {
        CustomUserDetails nonSupport = user(7L, Map.of());

        assertThrows(AccessDeniedException.class,
                () -> chatService.replyToConversation(nonSupport, 9L, "Phan hoi"));

        verify(conversationRepository, never()).findById(any());
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void supportReplyPersistsInTheSelectedConversation() {
        CustomUserDetails support = user(7L, Map.of(FunctionCode.AI_CHAT, ActionCode.CREATE));
        SupportConversation conversation = conversation(9L, 42L, "Dat phong", "2026-08-04T10:00:00Z");
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(42L)).thenReturn(Optional.of(customerEntity(42L)));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> savedMessage(
                invocation.getArgument(0), 100L, Instant.parse("2026-08-04T10:05:00Z")));
        when(conversationRepository.save(conversation)).thenReturn(conversation);

        ChatMessageDTO reply = chatService.replyToConversation(support, 9L, " Da tiep nhan ");

        assertEquals(9L, reply.getConversationId());
        assertEquals(7L, reply.getSenderId());
        assertEquals(42L, reply.getReceiverId());
        assertEquals("Da tiep nhan", reply.getContent());
    }

    private SupportConversation conversation(Long id, Long customerId, String subject, String updatedAt) {
        SupportConversation conversation = new SupportConversation();
        conversation.setId(id);
        conversation.setCustomerId(customerId);
        conversation.setSubject(subject);
        conversation.setCreatedAt(Instant.parse(updatedAt));
        conversation.setUpdatedAt(Instant.parse(updatedAt));
        return conversation;
    }

    private ChatMessage message(
            Long id, Long conversationId, Long senderId, Long receiverId, String content, String timestamp) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setTimestamp(Instant.parse(timestamp));
        return message;
    }

    private ChatMessage savedMessage(ChatMessage message, Long id, Instant timestamp) {
        message.setId(id);
        message.setTimestamp(timestamp);
        return message;
    }

    private User customerEntity(Long id) {
        User customer = new User();
        customer.setId(id);
        customer.setUsername("customer" + id);
        customer.setFullName("Customer " + id);
        return customer;
    }

    private CustomUserDetails user(Long id, Map<FunctionCode, Integer> masks) {
        return new CustomUserDetails(
                "user" + id,
                "hash",
                Set.of(new SimpleGrantedAuthority("CUSTOMER")),
                masks,
                id,
                null,
                Map.of());
    }
}

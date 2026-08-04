package com.hotel.controllers;

import com.hotel.dtos.ChatMessageDTO;
import com.hotel.dtos.CustomerChatMessageRequest;
import com.hotel.dtos.SupportChatReplyRequest;
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private ChatService chatService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatController controller;
    private CustomUserDetails customer;
    private CustomUserDetails support;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService, messagingTemplate, new ChatAuthorizationService());
        customer = user(42L, "customer");
        support = user(7L, "support");
    }

    @Test
    void customerMessageUsesSelectedConversationAndIsAcknowledged() {
        CustomerChatMessageRequest request = new CustomerChatMessageRequest();
        request.setConversationId(9L);
        request.setContent("Can ho tro");
        ChatMessageDTO saved = message(9L, 42L, 0L, "Can ho tro");
        when(chatService.sendToSupport(customer, 9L, "Can ho tro")).thenReturn(saved);
        when(chatService.getSupportRecipients(5L)).thenReturn(java.util.List.of("support"));

        controller.sendToSupport(request, authentication(customer));

        verify(messagingTemplate).convertAndSendToUser("customer", "/queue/messages", saved);
        verify(messagingTemplate).convertAndSendToUser("support", "/queue/support/messages", saved);
    }

    @Test
    void supportReplyUsesConversationCustomerForThePrivateDestination() {
        SupportChatReplyRequest request = new SupportChatReplyRequest();
        request.setConversationId(9L);
        request.setContent("Da tiep nhan");
        ChatMessageDTO saved = message(9L, 7L, 42L, "Da tiep nhan");
        when(chatService.replyToConversation(support, 9L, "Da tiep nhan", null)).thenReturn(saved);
        when(chatService.getConversationCustomerId(9L)).thenReturn(42L);
        when(chatService.getUsername(42L)).thenReturn("customer");
        when(chatService.getSupportRecipients(5L)).thenReturn(java.util.List.of("support"));

        controller.replyToCustomer(request, authentication(support));

        verify(messagingTemplate).convertAndSendToUser("customer", "/queue/messages", saved);
        verify(messagingTemplate).convertAndSendToUser("support", "/queue/support/messages", saved);
    }

    @Test
    void customerRestSendPersistsAndBroadcastsTheSelectedConversation() {
        CustomerChatMessageRequest request = new CustomerChatMessageRequest();
        request.setContent("Can ho tro qua HTTP");
        ChatMessageDTO saved = message(9L, 42L, 0L, "Can ho tro qua HTTP");
        when(chatService.sendToSupport(customer, 9L, "Can ho tro qua HTTP")).thenReturn(saved);
        when(chatService.getSupportRecipients(5L)).thenReturn(java.util.List.of("support"));

        controller.sendMyConversationMessage(authentication(customer), 9L, request);

        verify(messagingTemplate).convertAndSendToUser("customer", "/queue/messages", saved);
        verify(messagingTemplate).convertAndSendToUser("support", "/queue/support/messages", saved);
    }

    @Test
    void supportRestSendPersistsAndNotifiesTheOwningCustomer() {
        SupportChatReplyRequest request = new SupportChatReplyRequest();
        request.setContent("Da tiep nhan qua HTTP");
        ChatMessageDTO saved = message(9L, 7L, 42L, "Da tiep nhan qua HTTP");
        when(chatService.replyToConversation(support, 9L, "Da tiep nhan qua HTTP", null)).thenReturn(saved);
        when(chatService.getConversationCustomerId(9L)).thenReturn(42L);
        when(chatService.getUsername(42L)).thenReturn("customer");
        when(chatService.getSupportRecipients(5L)).thenReturn(java.util.List.of("support"));

        controller.sendSupportConversationMessage(9L, request, authentication(support));

        verify(messagingTemplate).convertAndSendToUser("customer", "/queue/messages", saved);
        verify(messagingTemplate).convertAndSendToUser("support", "/queue/support/messages", saved);
    }

    private UsernamePasswordAuthenticationToken authentication(CustomUserDetails user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private CustomUserDetails user(Long id, String username) {
        return new CustomUserDetails(
                username,
                "hash",
                Set.of(new SimpleGrantedAuthority("CUSTOMER")),
                Map.of(),
                id,
                null,
                Map.of());
    }

    private ChatMessageDTO message(Long conversationId, Long senderId, Long receiverId, String content) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(1L);
        dto.setConversationId(conversationId);
        dto.setHotelId(5L);
        dto.setSenderId(senderId);
        dto.setReceiverId(receiverId);
        dto.setContent(content);
        return dto;
    }
}

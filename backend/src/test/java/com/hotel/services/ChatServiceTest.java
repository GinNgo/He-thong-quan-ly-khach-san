package com.hotel.services;

import com.hotel.dtos.ChatMessageDTO;
import com.hotel.entities.ChatMessage;
import com.hotel.entities.User;
import com.hotel.repositories.ChatMessageRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatMessageRepository, userRepository, new ChatAuthorizationService());
    }

    @Test
    void sendToSupportAlwaysUsesAuthenticatedSenderAndCentralQueue() {
        CustomUserDetails customer = user(42L, Map.of());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(99L);
            message.setTimestamp(Instant.parse("2026-07-28T10:00:00Z"));
            return message;
        });

        ChatMessageDTO result = chatService.sendToSupport(customer, "  Xin chao  ");

        assertEquals(42L, result.getSenderId());
        assertEquals(0L, result.getReceiverId());
        assertEquals("Xin chao", result.getContent());
    }

    @Test
    void supportReplyRequiresAiChatCreatePermission() {
        CustomUserDetails nonSupport = user(7L, Map.of());

        assertThrows(AccessDeniedException.class,
                () -> chatService.replyToCustomer(nonSupport, 42L, "Phan hoi"));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void supportCanOnlyReplyToCustomerAlreadyInCentralQueue() {
        CustomUserDetails support = user(7L, Map.of(FunctionCode.AI_CHAT, ActionCode.CREATE));
        when(chatMessageRepository.existsBySenderIdAndReceiverId(42L, 0L)).thenReturn(false);

        assertThrows(com.hotel.exceptions.ResourceNotFoundException.class,
                () -> chatService.replyToCustomer(support, 42L, "Phan hoi"));
    }

    @Test
    void myHistoryAlwaysUsesAuthenticatedUserId() {
        CustomUserDetails customer = user(42L, Map.of());
        when(chatMessageRepository.findCustomerSupportHistory(42L)).thenReturn(List.of());

        chatService.getMyHistory(customer);

        verify(chatMessageRepository).findCustomerSupportHistory(42L);
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

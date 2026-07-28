package com.hotel.controllers;

import com.hotel.dtos.ChatConversationDTO;
import com.hotel.dtos.ChatMessageDTO;
import com.hotel.dtos.CustomerChatMessageRequest;
import com.hotel.dtos.SupportChatReplyRequest;
import com.hotel.security.ActionCode;
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatAuthorizationService authorizationService;

    @MessageMapping("/chat.support.send")
    public void sendToSupport(
            @Valid @Payload CustomerChatMessageRequest request,
            Principal principal) {
        CustomUserDetails customer = authorizationService.requireUser(principal);
        ChatMessageDTO savedMessage = chatService.sendToSupport(customer, request.getContent());
        messagingTemplate.convertAndSendToUser(
                customer.getUsername(),
                "/queue/messages",
                savedMessage);
        messagingTemplate.convertAndSend("/topic/support/messages", savedMessage);
    }

    @MessageMapping("/chat.support.reply")
    public void replyToCustomer(
            @Valid @Payload SupportChatReplyRequest request,
            Principal principal) {
        CustomUserDetails support = authorizationService.requireUser(principal);
        ChatMessageDTO savedMessage = chatService.replyToCustomer(
                support,
                request.getCustomerId(),
                request.getContent());
        messagingTemplate.convertAndSendToUser(
                chatService.getUsername(request.getCustomerId()),
                "/queue/messages",
                savedMessage);
        messagingTemplate.convertAndSend("/topic/support/messages", savedMessage);
    }

    @GetMapping("/api/chat/me/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageDTO>> getMyHistory(Principal principal) {
        return ResponseEntity.ok(chatService.getMyHistory(authorizationService.requireUser(principal)));
    }

    @GetMapping("/api/chat/support/conversations")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<List<ChatConversationDTO>> getSupportConversations(Principal principal) {
        return ResponseEntity.ok(chatService.getSupportConversations(authorizationService.requireUser(principal)));
    }

    @GetMapping("/api/chat/support/conversations/{customerId}")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<List<ChatMessageDTO>> getSupportHistory(
            @PathVariable Long customerId,
            Principal principal) {
        return ResponseEntity.ok(chatService.getSupportHistory(
                authorizationService.requireUser(principal),
                customerId));
    }
}

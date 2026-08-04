package com.hotel.controllers;

import com.hotel.dtos.ChatConversationCreateRequest;
import com.hotel.dtos.ChatConversationDTO;
import com.hotel.dtos.ChatMessageDTO;
import com.hotel.dtos.ChatPageDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        ChatMessageDTO savedMessage = request.getConversationId() == null
                ? chatService.sendToSupport(
                        customer, request.getHotelId(), request.getReservationId(), request.getContent())
                : chatService.sendToSupport(customer, request.getConversationId(), request.getContent());
        messagingTemplate.convertAndSendToUser(customer.getUsername(), "/queue/messages", savedMessage);
        publishToSupportRecipients(savedMessage);
    }

    @MessageMapping("/chat.support.reply")
    public void replyToCustomer(
            @Valid @Payload SupportChatReplyRequest request,
            Principal principal) {
        CustomUserDetails support = authorizationService.requireUser(principal);
        ChatMessageDTO savedMessage = chatService.replyToConversation(
                support, request.getConversationId(), request.getContent(), request.getExpectedVersion());
        Long customerId = chatService.getConversationCustomerId(request.getConversationId());
        messagingTemplate.convertAndSendToUser(
                chatService.getUsername(customerId), "/queue/messages", savedMessage);
        publishToSupportRecipients(savedMessage);
    }

    @GetMapping("/api/chat/me/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageDTO>> getMyHistory(Principal principal) {
        return ResponseEntity.ok(chatService.getMyHistory(authorizationService.requireUser(principal)));
    }

    @GetMapping("/api/chat/me/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatPageDTO<ChatConversationDTO>> getMyConversations(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(chatService.getMyConversations(
                authorizationService.requireUser(principal), page, size));
    }

    @PostMapping("/api/chat/me/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatConversationDTO> createMyConversation(
            Principal principal,
            @Valid @RequestBody ChatConversationCreateRequest request) {
        return ResponseEntity.ok(chatService.createConversation(
                authorizationService.requireUser(principal),
                request.subject(), request.hotelId(), request.reservationId()));
    }

    @GetMapping("/api/chat/me/conversations/{conversationId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatPageDTO<ChatMessageDTO>> getMyConversationMessages(
            Principal principal,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatService.getMyConversationMessages(
                authorizationService.requireUser(principal), conversationId, page, size));
    }

    @PostMapping("/api/chat/me/conversations/{conversationId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageDTO> sendMyConversationMessage(
            Principal principal,
            @PathVariable Long conversationId,
            @Valid @RequestBody CustomerChatMessageRequest request) {
        CustomUserDetails customer = authorizationService.requireUser(principal);
        ChatMessageDTO savedMessage = chatService.sendToSupport(customer, conversationId, request.getContent());
        messagingTemplate.convertAndSendToUser(customer.getUsername(), "/queue/messages", savedMessage);
        publishToSupportRecipients(savedMessage);
        return ResponseEntity.ok(savedMessage);
    }

    @GetMapping("/api/chat/support/conversations")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<List<ChatConversationDTO>> getSupportConversations(
            Principal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "ALL") String assignment,
            @RequestParam(defaultValue = "ALL") String sla,
            @RequestParam(required = false) Long hotelId) {
        return ResponseEntity.ok(chatService.getSupportConversations(
                authorizationService.requireUser(principal), status, assignment, sla, hotelId));
    }

    @GetMapping("/api/chat/support/conversations/{conversationId}")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<List<ChatMessageDTO>> getSupportHistory(
            @PathVariable Long conversationId,
            Principal principal) {
        return ResponseEntity.ok(chatService.getSupportHistory(
                authorizationService.requireUser(principal), conversationId));
    }

    @GetMapping("/api/chat/support/conversations/{conversationId}/messages")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<ChatPageDTO<ChatMessageDTO>> getSupportConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Principal principal) {
        return ResponseEntity.ok(chatService.getSupportConversationMessages(
                authorizationService.requireUser(principal), conversationId, page, size));
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/messages")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatMessageDTO> sendSupportConversationMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SupportChatReplyRequest request,
            Principal principal) {
        ChatMessageDTO savedMessage = chatService.replyToConversation(
                authorizationService.requireUser(principal), conversationId,
                request.getContent(), request.getExpectedVersion());
        Long customerId = chatService.getConversationCustomerId(conversationId);
        messagingTemplate.convertAndSendToUser(
                chatService.getUsername(customerId), "/queue/messages", savedMessage);
        publishToSupportRecipients(savedMessage);
        return ResponseEntity.ok(savedMessage);
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/assign")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatConversationDTO> assignConversation(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long expectedVersion,
            Principal principal) {
        return ResponseEntity.ok(chatService.claimConversation(
                authorizationService.requireUser(principal), conversationId, expectedVersion));
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/unassign")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatConversationDTO> unassignConversation(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long expectedVersion,
            Principal principal) {
        return ResponseEntity.ok(chatService.unassignConversation(
                authorizationService.requireUser(principal), conversationId, expectedVersion));
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/escalate")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatConversationDTO> escalateConversation(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long expectedVersion,
            Principal principal) {
        return ResponseEntity.ok(chatService.escalateConversation(
                authorizationService.requireUser(principal), conversationId, expectedVersion));
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/reopen")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatConversationDTO> reopenConversation(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long expectedVersion,
            Principal principal) {
        return ResponseEntity.ok(chatService.reopenConversation(
                authorizationService.requireUser(principal), conversationId, expectedVersion));
    }

    private void publishToSupportRecipients(ChatMessageDTO message) {
        List<String> recipients = chatService.getSupportRecipients(message.getHotelId());
        if (recipients == null) return;
        recipients.forEach(username ->
                messagingTemplate.convertAndSendToUser(username, "/queue/support/messages", message));
    }
}

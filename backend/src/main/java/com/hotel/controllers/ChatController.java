package com.hotel.controllers;

import com.hotel.dtos.ChatConversationCreateRequest;
import com.hotel.dtos.ChatConversationDTO;
import com.hotel.dtos.ChatMessageDTO;
import com.hotel.dtos.ChatPageDTO;
import com.hotel.dtos.CustomerChatMessageRequest;
import com.hotel.dtos.SupportChatReplyRequest;
import com.hotel.dtos.SupportAttachmentDTO;
import com.hotel.dtos.SupportConversationLifecycleRequest;
import com.hotel.security.ActionCode;
import com.hotel.security.ChatAuthorizationService;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.ChatService;
import com.hotel.services.SupportAttachmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatAuthorizationService authorizationService;
    private final SupportAttachmentService attachmentService;

    @MessageMapping("/chat.support.send")
    public void sendToSupport(
            @Valid @Payload CustomerChatMessageRequest request,
            Principal principal) {
        CustomUserDetails customer = authorizationService.requireUser(principal);
<<<<<<< HEAD
        ChatMessageDTO savedMessage = request.getConversationId() == null
                ? chatService.sendToSupport(
                        customer, request.getHotelId(), request.getReservationId(), request.getContent(),
                        request.getClientMessageId())
                : chatService.sendToSupport(
                        customer, request.getConversationId(), request.getContent(), request.getClientMessageId());
        messagingTemplate.convertAndSendToUser(customer.getUsername(), "/queue/messages", savedMessage);
        publishToSupportRecipients(savedMessage);
=======
        ChatMessageDTO savedMessage = chatService.sendToSupport(
                customer,
                request.getHotelId(),
                request.getReservationId(),
                request.getContent());
        messagingTemplate.convertAndSendToUser(
                customer.getUsername(),
                "/queue/messages",
                savedMessage);
        chatService.getSupportRecipients(savedMessage.getHotelId()).forEach(username ->
                messagingTemplate.convertAndSendToUser(
                        username,
                        "/queue/support/messages",
                        savedMessage));
>>>>>>> codex/ui-functional-audit-polish
    }

    @MessageMapping("/chat.support.reply")
    public void replyToCustomer(
            @Valid @Payload SupportChatReplyRequest request,
            Principal principal) {
        CustomUserDetails support = authorizationService.requireUser(principal);
<<<<<<< HEAD
        ChatMessageDTO savedMessage = chatService.replyToConversation(
                support, request.getConversationId(), request.getContent(), request.getExpectedVersion(),
                request.getClientMessageId());
        Long customerId = chatService.getConversationCustomerId(request.getConversationId());
        messagingTemplate.convertAndSendToUser(
                chatService.getUsername(customerId), "/queue/messages", savedMessage);
        publishToSupportRecipients(savedMessage);
=======
        ChatMessageDTO savedMessage = chatService.replyToCustomer(
                support,
                request.getConversationId(),
                request.getContent());
        messagingTemplate.convertAndSendToUser(
                chatService.getUsername(savedMessage.getReceiverId()),
                "/queue/messages",
                savedMessage);
        messagingTemplate.convertAndSendToUser(
                support.getUsername(),
                "/queue/support/messages",
                savedMessage);
>>>>>>> codex/ui-functional-audit-polish
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

<<<<<<< HEAD
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
        ChatMessageDTO savedMessage = chatService.sendToSupport(
                customer, conversationId, request.getContent(), request.getClientMessageId());
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
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(chatService.getSupportConversations(
                authorizationService.requireUser(principal), status, assignment, sla, hotelId, query));
    }

=======
>>>>>>> codex/ui-functional-audit-polish
    @GetMapping("/api/chat/support/conversations/{conversationId}")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<List<ChatMessageDTO>> getSupportHistory(
            @PathVariable Long conversationId,
            Principal principal) {
        return ResponseEntity.ok(chatService.getSupportHistory(
<<<<<<< HEAD
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

    @GetMapping("/api/chat/support/conversations/{conversationId}/events")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<org.springframework.data.domain.Page<com.hotel.dtos.SupportConversationEventDTO>> getSupportAuditHistory(
            @PathVariable Long conversationId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatService.supportAuditHistory(conversationId, page, size));
    }

    @GetMapping("/api/chat/support/audit-policy")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<java.util.Map<String, Object>> getSupportAuditPolicy() {
        return ResponseEntity.ok(chatService.supportAuditPolicy());
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/messages")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatMessageDTO> sendSupportConversationMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SupportChatReplyRequest request,
            Principal principal) {
        ChatMessageDTO savedMessage = chatService.replyToConversation(
                authorizationService.requireUser(principal), conversationId,
                request.getContent(), request.getExpectedVersion(), request.getClientMessageId());
        Long customerId = chatService.getConversationCustomerId(conversationId);
        messagingTemplate.convertAndSendToUser(
                chatService.getUsername(customerId), "/queue/messages", savedMessage);
        publishToSupportRecipients(savedMessage);
        return ResponseEntity.ok(savedMessage);
=======
                authorizationService.requireUser(principal),
                conversationId));
>>>>>>> codex/ui-functional-audit-polish
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/assign")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatConversationDTO> assignConversation(
            @PathVariable Long conversationId,
<<<<<<< HEAD
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
=======
            Principal principal) {
        return ResponseEntity.ok(chatService.claimConversation(
                authorizationService.requireUser(principal),
                conversationId));
>>>>>>> codex/ui-functional-audit-polish
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/escalate")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatConversationDTO> escalateConversation(
            @PathVariable Long conversationId,
<<<<<<< HEAD
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
            @Valid @RequestBody SupportConversationLifecycleRequest request,
            Principal principal) {
        return ResponseEntity.ok(chatService.reopenConversation(
                authorizationService.requireUser(principal), conversationId, expectedVersion, request.reason()));
    }

    @PostMapping("/api/chat/support/conversations/{conversationId}/close")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatConversationDTO> closeConversation(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long expectedVersion,
            @Valid @RequestBody SupportConversationLifecycleRequest request,
            Principal principal) {
        return ResponseEntity.ok(chatService.closeConversation(
                authorizationService.requireUser(principal), conversationId, expectedVersion, request.reason()));
    }

    @GetMapping("/api/chat/me/conversations/{conversationId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SupportAttachmentDTO>> getMyAttachments(
            @PathVariable Long conversationId, Principal principal) {
        return ResponseEntity.ok(attachmentService.listForCustomer(
                authorizationService.requireUser(principal), conversationId));
    }

    @PostMapping(value = "/api/chat/me/conversations/{conversationId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportAttachmentDTO> uploadMyAttachment(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        return ResponseEntity.ok(attachmentService.uploadForCustomer(
                authorizationService.requireUser(principal), conversationId, file));
    }

    @GetMapping("/api/chat/support/conversations/{conversationId}/attachments")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.VIEW)
    public ResponseEntity<List<SupportAttachmentDTO>> getSupportAttachments(
            @PathVariable Long conversationId, Principal principal) {
        return ResponseEntity.ok(attachmentService.listForSupport(
                authorizationService.requireUser(principal), conversationId));
    }

    @PostMapping(value = "/api/chat/support/conversations/{conversationId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<SupportAttachmentDTO> uploadSupportAttachment(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        return ResponseEntity.ok(attachmentService.uploadForSupport(
                authorizationService.requireUser(principal), conversationId, file));
    }

    @GetMapping("/api/chat/attachments/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long attachmentId, Principal principal) {
        SupportAttachmentService.AttachmentContent attachment = attachmentService.download(
                authorizationService.requireUser(principal), attachmentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(attachment.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(attachment.filename(), java.nio.charset.StandardCharsets.UTF_8).build());
        headers.setContentLength(attachment.bytes().length);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Content-SHA256", attachment.checksumSha256());
        return ResponseEntity.ok().headers(headers).body(attachment.bytes());
    }

    @PostMapping("/api/chat/messages/{messageId}/state")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageDTO> acknowledgeMessage(
            @PathVariable Long messageId,
            @RequestParam String state,
            Principal principal) {
        ChatMessageDTO message = chatService.acknowledgeMessage(
                authorizationService.requireUser(principal), messageId, state);
        messagingTemplate.convertAndSendToUser(
                chatService.getUsername(message.getSenderId()), "/queue/messages", message);
        publishToSupportRecipients(message);
        return ResponseEntity.ok(message);
    }

    private void publishToSupportRecipients(ChatMessageDTO message) {
        List<String> recipients = chatService.getSupportRecipients(message.getHotelId());
        if (recipients == null) return;
        recipients.forEach(username ->
                messagingTemplate.convertAndSendToUser(username, "/queue/support/messages", message));
=======
            Principal principal) {
        return ResponseEntity.ok(chatService.escalateConversation(
                authorizationService.requireUser(principal),
                conversationId));
>>>>>>> codex/ui-functional-audit-polish
    }
}

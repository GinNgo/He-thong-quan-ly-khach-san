package com.hotel.controllers;

import com.hotel.dtos.ChatMessageDTO;
import com.hotel.services.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // WebSocket Endpoint: /app/chat.sendMessage
    @MessageMapping("/chat.sendMessage")
    public void processMessage(@Payload ChatMessageDTO chatMessageDTO) {
        // Save the message
        ChatMessageDTO savedMsg = chatService.saveMessage(chatMessageDTO);
        
        // Send to intended recipient
        // Stomp subscribe path: /user/{userId}/queue/messages
        messagingTemplate.convertAndSendToUser(
                String.valueOf(savedMsg.getReceiverId()), 
                "/queue/messages", 
                savedMsg
        );
    }

    // REST Endpoint to fetch chat history between two users
    @GetMapping("/api/chat/history/{userId1}/{userId2}")
    public ResponseEntity<List<ChatMessageDTO>> getChatHistory(
            @PathVariable Long userId1, 
            @PathVariable Long userId2) {
        return ResponseEntity.ok(chatService.getChatHistory(userId1, userId2));
    }

    // REST Endpoint to fetch all distinct users who have chatted with a specific Admin
    @GetMapping("/api/chat/active-users/{adminId}")
    public ResponseEntity<List<Long>> getActiveUsers(@PathVariable Long adminId) {
        return ResponseEntity.ok(chatService.getActiveChatUsers(adminId));
    }
}

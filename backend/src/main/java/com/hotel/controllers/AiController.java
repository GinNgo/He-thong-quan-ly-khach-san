package com.hotel.controllers;

import com.hotel.dtos.ChatRequest;
import com.hotel.dtos.ChatResponse;
import com.hotel.services.AiService;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AiController {

    private final AiService aiService;
    private final com.hotel.services.NotificationService notificationService;

    public AiController(AiService aiService, com.hotel.services.NotificationService notificationService) {
        this.aiService = aiService;
        this.notificationService = notificationService;
    }

    @PostMapping("/chat")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatResponse> chat(Authentication authentication, @RequestBody ChatRequest request) {
        String username = authentication != null ? authentication.getName() : "Guest";
        
        notificationService.sendSystemNotification(
            "CHAT", 
            "Tin nhắn hỗ trợ mới", 
            "Khách hàng " + username + " vừa gửi tin nhắn: " + (request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) + "..." : request.getMessage())
        );

        ChatResponse response = aiService.processMessage(username, request);
        return ResponseEntity.ok(response);
    }
}

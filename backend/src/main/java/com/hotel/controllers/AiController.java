package com.hotel.controllers;

import com.hotel.dtos.ChatRequest;
import com.hotel.dtos.ChatResponse;
import com.hotel.services.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<ChatResponse> chat(Authentication authentication, @RequestBody ChatRequest request) {
        String username = authentication != null ? authentication.getName() : "Guest";
        ChatResponse response = aiService.processMessage(username, request);
        return ResponseEntity.ok(response);
    }
}

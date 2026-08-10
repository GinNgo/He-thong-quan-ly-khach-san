package com.hotel.controllers;

import com.hotel.dtos.ChatRequest;
import com.hotel.dtos.ChatResponse;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.AiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AiController {
    private static final MediaType UTF8_TEXT = MediaType.parseMediaType("text/plain;charset=UTF-8");
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    @Permission(function = FunctionCode.AI_CHAT, action = ActionCode.CREATE)
    public ResponseEntity<ChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody ChatRequest request) {
        String username = authentication != null ? authentication.getName() : "Guest";
        return ResponseEntity.ok(aiService.processMessage(username, request));
    }

    @PostMapping("/customer/chat")
    public ResponseEntity<ChatResponse> customerChat(
            Authentication authentication,
            @Valid @RequestBody ChatRequest request) {
        String username = authentication != null ? authentication.getName() : "Guest";
        return ResponseEntity.ok(aiService.processCustomerMessage(username, request));
    }

    @PostMapping(value = "/customer/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter customerChatStream(
            Authentication authentication,
            @Valid @RequestBody ChatRequest request) {
        String username = authentication != null ? authentication.getName() : "Guest";
        SseEmitter emitter = new SseEmitter(60_000L);

        Thread.ofVirtual().name("customer-ai-stream").start(() -> {
            try {
                aiService.streamCustomerMessage(username, request, chunk -> sendChunk(emitter, chunk));
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    private void sendChunk(SseEmitter emitter, String chunk) {
        try {
            emitter.send(SseEmitter.event().name("message").data(chunk, UTF8_TEXT));
        } catch (IOException exception) {
            throw new IllegalStateException("Kết nối SSE đã đóng", exception);
        }
    }
}

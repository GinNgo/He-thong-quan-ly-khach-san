package com.hotel.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class ChatRequest {
    @NotBlank
    @Size(max = 500)
    private String message;

    @Valid
    @Size(max = 10)
    private List<ChatHistoryMessage> history = new ArrayList<>();

    public ChatRequest() {}

    public ChatRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ChatHistoryMessage> getHistory() {
        return history;
    }

    public void setHistory(List<ChatHistoryMessage> history) {
        this.history = history == null ? new ArrayList<>() : history;
    }

    public static class ChatHistoryMessage {
        @NotBlank
        private String role;

        @NotBlank
        @Size(max = 2000)
        private String text;

        public ChatHistoryMessage() {}

        public ChatHistoryMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}

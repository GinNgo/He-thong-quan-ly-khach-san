package com.hotel.services;

import com.hotel.dtos.ChatRequest;
import com.hotel.dtos.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    // In a real application, this would use Spring AI or a RestTemplate to call OpenAI / Gemini APIs.
    // For this mockup, we return predefined logic based on keywords.

    public ChatResponse processMessage(String username, ChatRequest request) {
        String msg = request.getMessage().toLowerCase();
        String reply;

        if (msg.contains("recommend") && msg.contains("room")) {
            reply = "Welcome back, " + username + ". Based on your recent stays, here are some Smart Recommendations for your upcoming trip:\n\n" +
                    "- **The Ritz-Carlton, Central Park** - Deluxe Park View Suite with 24-hour butler service.\n" +
                    "- **Private Dining Experience** at Le Bernardin.";
        } else if (msg.contains("booking") || msg.contains("cancel")) {
            reply = "I can certainly help you with your booking. Would you like to modify your upcoming stay, or cancel an existing reservation?";
        } else if (msg.contains("weather")) {
            reply = "The current local weather is 68°F and Partly Cloudy.";
        } else {
            reply = "I'm the Aurum Virtual Assistant. I can help you with room recommendations, booking modifications, or local travel updates. How may I assist you today?";
        }

        return new ChatResponse(reply);
    }
}

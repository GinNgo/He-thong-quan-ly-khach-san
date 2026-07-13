package com.hotel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Hỗ trợ gửi thông báo tới các client đang lắng nghe trên /topic/... hoặc /user/...
        config.enableSimpleBroker("/topic", "/user");
        // Các message từ client gửi lên sẽ bắt đầu bằng /app/...
        config.setApplicationDestinationPrefixes("/app");
        // Tiền tố dùng để gửi tin nhắn đến user cụ thể
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint cho client (Angular) kết nối tới
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Cho phép CORS
                .withSockJS(); // Fallback nếu browser không hỗ trợ WebSocket thuần
    }
}

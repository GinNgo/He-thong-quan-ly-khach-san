package com.hotel.config;

import com.hotel.security.ChatChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatChannelInterceptor chatChannelInterceptor;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            ChatChannelInterceptor chatChannelInterceptor,
            ChatHandshakeInterceptor chatHandshakeInterceptor,
            @Value("${app.websocket.allowed-origins:http://localhost:4200}") String[] allowedOrigins) {
        this.chatChannelInterceptor = chatChannelInterceptor;
        this.chatHandshakeInterceptor = chatHandshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
        registry.addEndpoint("/ws-chat")
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}

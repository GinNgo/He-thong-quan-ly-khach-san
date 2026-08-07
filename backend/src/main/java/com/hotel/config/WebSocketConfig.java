package com.hotel.config;

import com.hotel.observability.StompObservabilityInterceptor;
import com.hotel.security.ChatChannelInterceptor;
import com.hotel.security.NotificationChannelInterceptor;
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

    private final StompObservabilityInterceptor stompObservabilityInterceptor;
    private final ChatChannelInterceptor chatChannelInterceptor;
    private final NotificationChannelInterceptor notificationChannelInterceptor;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
<<<<<<< HEAD
=======
    private final NotificationChannelInterceptor notificationChannelInterceptor;
>>>>>>> codex/ui-functional-audit-polish
    private final NotificationHandshakeInterceptor notificationHandshakeInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            StompObservabilityInterceptor stompObservabilityInterceptor,
            ChatChannelInterceptor chatChannelInterceptor,
            NotificationChannelInterceptor notificationChannelInterceptor,
            ChatHandshakeInterceptor chatHandshakeInterceptor,
<<<<<<< HEAD
=======
            NotificationChannelInterceptor notificationChannelInterceptor,
>>>>>>> codex/ui-functional-audit-polish
            NotificationHandshakeInterceptor notificationHandshakeInterceptor,
            @Value("${app.websocket.allowed-origins:http://localhost:4200}") String[] allowedOrigins) {
        this.stompObservabilityInterceptor = stompObservabilityInterceptor;
        this.chatChannelInterceptor = chatChannelInterceptor;
        this.notificationChannelInterceptor = notificationChannelInterceptor;
        this.chatHandshakeInterceptor = chatHandshakeInterceptor;
<<<<<<< HEAD
=======
        this.notificationChannelInterceptor = notificationChannelInterceptor;
>>>>>>> codex/ui-functional-audit-polish
        this.notificationHandshakeInterceptor = notificationHandshakeInterceptor;
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
<<<<<<< HEAD
        registration.interceptors(stompObservabilityInterceptor, chatChannelInterceptor, notificationChannelInterceptor);
=======
        registration.interceptors(
                stompObservabilityInterceptor,
                chatChannelInterceptor,
                notificationChannelInterceptor);
>>>>>>> codex/ui-functional-audit-polish
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(notificationHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS()
                .setSessionCookieNeeded(false);
        registry.addEndpoint("/ws-chat")
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS()
                .setSessionCookieNeeded(false);
    }
}

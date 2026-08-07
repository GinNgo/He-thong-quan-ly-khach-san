package com.hotel.security;

import com.hotel.config.ChatHandshakeInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final String CUSTOMER_SEND_DESTINATION = "/app/chat.support.send";
    private static final String SUPPORT_REPLY_DESTINATION = "/app/chat.support.reply";
    private static final String CUSTOMER_QUEUE_DESTINATION = "/user/queue/messages";
    private static final String SUPPORT_QUEUE_DESTINATION = "/user/queue/support/messages";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final ChatAuthorizationService authorizationService;

    public ChatChannelInterceptor(
            JwtTokenProvider jwtTokenProvider,
            UserDetailsService userDetailsService,
            ChatAuthorizationService authorizationService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.authorizationService = authorizationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, SimpMessageHeaderAccessor.class);
        boolean originalAccessor = accessor != null;
        if (accessor == null) {
            accessor = SimpMessageHeaderAccessor.wrap(message);
        }
        if (!isChatSession(accessor)) {
            return message;
        }

        SimpMessageType messageType = accessor.getMessageType();
        if (messageType == SimpMessageType.CONNECT) {
            authenticate(accessor);
            return originalAccessor
                    ? message
                    : MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }

        if (messageType == SimpMessageType.DISCONNECT || messageType == SimpMessageType.HEARTBEAT) {
            return message;
        }

        CustomUserDetails userDetails = authorizationService.requireUser(accessor.getUser());
        String destination = accessor.getDestination();

        if (messageType == SimpMessageType.MESSAGE) {
            authorizeSend(userDetails, destination);
        } else if (messageType == SimpMessageType.SUBSCRIBE) {
            authorizeSubscribe(userDetails, destination);
        }

        return message;
    }

    private boolean isChatSession(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        return attributes != null
                && Boolean.TRUE.equals(attributes.get(ChatHandshakeInterceptor.CHAT_SESSION_ATTRIBUTE));
    }

    private void authenticate(SimpMessageHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AuthenticationCredentialsNotFoundException("Bearer token is required for chat");
        }

        String token = authorization.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new AuthenticationCredentialsNotFoundException("Invalid chat bearer token");
        }

        String username = jwtTokenProvider.getUsername(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());
        accessor.setUser(authentication);
    }

    private void authorizeSend(CustomUserDetails userDetails, String destination) {
        if (CUSTOMER_SEND_DESTINATION.equals(destination)) {
            return;
        }
        if (SUPPORT_REPLY_DESTINATION.equals(destination)) {
            authorizationService.requirePermission(userDetails, ActionCode.CREATE);
            return;
        }
        throw new AccessDeniedException("Chat send destination is not allowed");
    }

    private void authorizeSubscribe(CustomUserDetails userDetails, String destination) {
        if (CUSTOMER_QUEUE_DESTINATION.equals(destination)) {
            return;
        }
        if (SUPPORT_QUEUE_DESTINATION.equals(destination)) {
            authorizationService.requirePermission(userDetails, ActionCode.VIEW);
            return;
        }
        throw new AccessDeniedException("Chat subscription destination is not allowed");
    }
}

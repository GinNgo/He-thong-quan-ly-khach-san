package com.hotel.security;

import com.hotel.config.NotificationHandshakeInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class NotificationChannelInterceptor implements ChannelInterceptor {

    private static final String ADMIN_TOPIC = "/topic/admin/notifications";
    private static final String PERSONAL_QUEUE = "/user/queue/notifications";
    private static final String AUTHENTICATION_ATTRIBUTE =
            NotificationChannelInterceptor.class.getName() + ".AUTHENTICATION";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public NotificationChannelInterceptor(
            JwtTokenProvider jwtTokenProvider,
            UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, SimpMessageHeaderAccessor.class);
        boolean usingOriginalAccessor = accessor != null;
        if (accessor == null) accessor = SimpMessageHeaderAccessor.wrap(message);
        if (!isNotificationSession(accessor)) return message;

        SimpMessageType type = accessor.getMessageType();
        if (type == SimpMessageType.CONNECT) {
            authenticate(accessor);
            return authenticatedMessage(message, accessor, usingOriginalAccessor);
        }
        if (type == SimpMessageType.DISCONNECT || type == SimpMessageType.HEARTBEAT) return message;

        restoreAuthentication(accessor);
        CustomUserDetails user = requireUser(accessor.getUser());
        if (type == SimpMessageType.MESSAGE) {
            throw new AccessDeniedException("Notification publishing is server-only");
        }
        if (type == SimpMessageType.SUBSCRIBE) authorizeSubscription(user, accessor.getDestination());
        return authenticatedMessage(message, accessor, usingOriginalAccessor);
    }

    private boolean isNotificationSession(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        return attributes != null
                && Boolean.TRUE.equals(attributes.get(NotificationHandshakeInterceptor.NOTIFICATION_SESSION_ATTRIBUTE));
    }

    private void authenticate(SimpMessageHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authorization)) authorization = accessor.getFirstNativeHeader("authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AuthenticationCredentialsNotFoundException("Bearer token is required for notifications");
        }
        String token = authorization.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new AuthenticationCredentialsNotFoundException("Invalid notification bearer token");
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(jwtTokenProvider.getUsername(token));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        accessor.setUser(authentication);
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null) attributes.put(AUTHENTICATION_ATTRIBUTE, authentication);
    }

    private void restoreAuthentication(SimpMessageHeaderAccessor accessor) {
        if (accessor.getUser() != null) return;
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null && attributes.get(AUTHENTICATION_ATTRIBUTE) instanceof Authentication authentication) {
            accessor.setUser(authentication);
        }
    }

    private CustomUserDetails requireUser(java.security.Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserDetails user) {
            return user;
        }
        throw new AuthenticationCredentialsNotFoundException("Notification authentication is required");
    }

    private void authorizeSubscription(CustomUserDetails user, String destination) {
        if (PERSONAL_QUEUE.equals(destination)) return;
        if (ADMIN_TOPIC.equals(destination)) {
            int mask = user.getPermissionMasks().getOrDefault(FunctionCode.REPORT, 0);
            if ((mask & ActionCode.VIEW) == ActionCode.VIEW) return;
        }
        throw new AccessDeniedException("Notification subscription destination is not allowed");
    }

    private Message<?> authenticatedMessage(
            Message<?> message,
            SimpMessageHeaderAccessor accessor,
            boolean usingOriginalAccessor) {
        return usingOriginalAccessor
                ? message
                : MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }
}

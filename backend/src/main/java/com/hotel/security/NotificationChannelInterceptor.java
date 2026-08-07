package com.hotel.security;

import com.hotel.config.NotificationHandshakeInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
<<<<<<< HEAD
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
=======
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
>>>>>>> codex/ui-functional-audit-polish
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
<<<<<<< HEAD

import java.util.Map;

@Component
public class NotificationChannelInterceptor implements ChannelInterceptor {

    private static final String ADMIN_TOPIC = "/topic/admin/notifications";
    private static final String PERSONAL_QUEUE = "/user/queue/notifications";
    private static final String AUTHENTICATION_ATTRIBUTE =
            NotificationChannelInterceptor.class.getName() + ".AUTHENTICATION";
=======
import java.security.Principal;
import java.util.Map;

@Component
public class NotificationChannelInterceptor implements org.springframework.messaging.support.ChannelInterceptor {

    private static final String ADMIN_TOPIC = "/topic/admin/notifications";
    private static final String PERSONAL_QUEUE = "/user/queue/notifications";
>>>>>>> codex/ui-functional-audit-polish

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
<<<<<<< HEAD
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
=======
        boolean originalAccessor = accessor != null;
        if (accessor == null) {
            accessor = SimpMessageHeaderAccessor.wrap(message);
        }
        if (!isNotificationSession(accessor)) {
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

        CustomUserDetails userDetails = requireUser(accessor.getUser());
        String destination = accessor.getDestination();
        if (messageType == SimpMessageType.SUBSCRIBE) {
            authorizeSubscribe(userDetails, destination);
        } else if (messageType == SimpMessageType.MESSAGE) {
            throw new AccessDeniedException("Notification clients cannot publish messages");
        }
        return message;
>>>>>>> codex/ui-functional-audit-polish
    }

    private boolean isNotificationSession(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        return attributes != null
                && Boolean.TRUE.equals(attributes.get(NotificationHandshakeInterceptor.NOTIFICATION_SESSION_ATTRIBUTE));
    }

    private void authenticate(SimpMessageHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
<<<<<<< HEAD
        if (!StringUtils.hasText(authorization)) authorization = accessor.getFirstNativeHeader("authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AuthenticationCredentialsNotFoundException("Bearer token is required for notifications");
        }
=======
        if (!StringUtils.hasText(authorization)) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AuthenticationCredentialsNotFoundException("Bearer token is required for notifications");
        }

>>>>>>> codex/ui-functional-audit-polish
        String token = authorization.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new AuthenticationCredentialsNotFoundException("Invalid notification bearer token");
        }
<<<<<<< HEAD
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
=======

        String username = jwtTokenProvider.getUsername(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()));
    }

    private CustomUserDetails requireUser(Principal principal) {
        Authentication authentication = principal instanceof Authentication candidate ? candidate : null;
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new AuthenticationCredentialsNotFoundException("Authenticated notification principal is required");
    }

    private void authorizeSubscribe(CustomUserDetails userDetails, String destination) {
        if (PERSONAL_QUEUE.equals(destination)) {
            return;
        }
        if (ADMIN_TOPIC.equals(destination) && hasReportView(userDetails)) {
            return;
>>>>>>> codex/ui-functional-audit-polish
        }
        throw new AccessDeniedException("Notification subscription destination is not allowed");
    }

<<<<<<< HEAD
    private Message<?> authenticatedMessage(
            Message<?> message,
            SimpMessageHeaderAccessor accessor,
            boolean usingOriginalAccessor) {
        return usingOriginalAccessor
                ? message
                : MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
=======
    private boolean hasReportView(CustomUserDetails userDetails) {
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(authority -> "SUPER_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        if (isSuperAdmin) {
            return true;
        }
        Map<FunctionCode, Integer> masks = userDetails.getPermissionMasks();
        Integer mask = masks == null ? null : masks.get(FunctionCode.REPORT);
        return mask != null && (mask & ActionCode.VIEW) == ActionCode.VIEW;
>>>>>>> codex/ui-functional-audit-polish
    }
}

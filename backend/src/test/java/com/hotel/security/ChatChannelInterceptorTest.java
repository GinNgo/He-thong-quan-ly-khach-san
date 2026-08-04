package com.hotel.security;

import com.hotel.config.ChatHandshakeInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private MessageChannel channel;

    private ChatChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ChatChannelInterceptor(
                jwtTokenProvider,
                userDetailsService,
                new ChatAuthorizationService());
    }

    @Test
    void chatConnectWithoutBearerTokenIsRejected() {
        Message<byte[]> message = message(SimpMessageType.CONNECT, null, null, null);

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> interceptor.preSend(message, channel));
    }

    @Test
    void validChatConnectAttachesAuthenticatedPrincipal() {
        CustomUserDetails customer = customer();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("customer");
        when(userDetailsService.loadUserByUsername("customer")).thenReturn(customer);

        Message<?> result = interceptor.preSend(
                message(SimpMessageType.CONNECT, null, "Bearer valid-token", null), channel);

        assertNotNull(SimpMessageHeaderAccessor.wrap(result).getUser());
    }

    @Test
    void validChatConnectNotifiesTheWebSocketUserRegistry() {
        CustomUserDetails customer = customer();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("customer");
        when(userDetailsService.loadUserByUsername("customer")).thenReturn(customer);
        AtomicReference<java.security.Principal> registeredUser = new AtomicReference<>();
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
        accessor.setSessionAttributes(new HashMap<>(Map.of(
                ChatHandshakeInterceptor.CHAT_SESSION_ATTRIBUTE, true)));
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setUserChangeCallback(registeredUser::set);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        assertNotNull(registeredUser.get());
    }

    @Test
    void suspendedAccountCannotReconnectToChatWithAnExistingToken() {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("suspended");
        when(userDetailsService.loadUserByUsername("suspended"))
                .thenThrow(new AccountDisabledAuthenticationException());

        assertThrows(
                AccountDisabledAuthenticationException.class,
                () -> interceptor.preSend(
                        message(SimpMessageType.CONNECT, null, "Bearer valid-token", null),
                        channel));
    }

    @Test
    void customerCannotSubscribeToSupportQueue() {
        Message<byte[]> message = message(
                SimpMessageType.SUBSCRIBE,
                "/user/queue/support/messages",
                null,
                authentication(customer()));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void supportWithViewPermissionCanSubscribeToSupportQueue() {
        CustomUserDetails support = user(
                7L,
                Map.of(FunctionCode.AI_CHAT, ActionCode.VIEW),
                "SUPPORT");
        Message<byte[]> message = message(
                SimpMessageType.SUBSCRIBE,
                "/user/queue/support/messages",
                null,
                authentication(support));

        assertDoesNotThrow(() -> interceptor.preSend(message, channel));
    }

    @Test
    void authenticatedCustomerCanOnlyUseStandardUserQueue() {
        Message<byte[]> allowed = message(
                SimpMessageType.SUBSCRIBE,
                "/user/queue/messages",
                null,
                authentication(customer()));
        Message<byte[]> forged = message(
                SimpMessageType.SUBSCRIBE,
                "/user/42/queue/messages",
                null,
                authentication(customer()));

        assertDoesNotThrow(() -> interceptor.preSend(allowed, channel));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(forged, channel));
    }

    @Test
    void legacyNotificationSessionIsNotChangedByChatInterceptor() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
        accessor.setDestination("/topic/notifications");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(message, channel));
    }

    private Message<byte[]> message(
            SimpMessageType type,
            String destination,
            String authorization,
            UsernamePasswordAuthenticationToken user) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setSessionAttributes(new HashMap<>(Map.of(
                ChatHandshakeInterceptor.CHAT_SESSION_ATTRIBUTE, true)));
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UsernamePasswordAuthenticationToken authentication(CustomUserDetails user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private CustomUserDetails customer() {
        return user(42L, Map.of(), "CUSTOMER");
    }

    private CustomUserDetails user(Long id, Map<FunctionCode, Integer> masks, String authority) {
        return new CustomUserDetails(
                authority.toLowerCase(),
                "hash",
                Set.of(new SimpleGrantedAuthority(authority)),
                masks,
                id,
                null,
                Map.of());
    }
}

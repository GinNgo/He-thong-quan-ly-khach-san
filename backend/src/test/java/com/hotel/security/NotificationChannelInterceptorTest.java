package com.hotel.security;

import com.hotel.config.NotificationHandshakeInterceptor;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private MessageChannel channel;

    private NotificationChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new NotificationChannelInterceptor(jwtTokenProvider, userDetailsService);
    }

    @Test
    void notificationConnectWithoutBearerTokenIsRejected() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> interceptor.preSend(message(SimpMessageType.CONNECT, null, null, null), channel));
    }

    @Test
    void validNotificationConnectAttachesAuthenticatedPrincipal() {
        CustomUserDetails staff = staff(ActionCode.VIEW);
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("staff");
        when(userDetailsService.loadUserByUsername("staff")).thenReturn(staff);

        Message<?> result = interceptor.preSend(
                message(SimpMessageType.CONNECT, null, "Bearer valid-token", null), channel);

        assertNotNull(SimpMessageHeaderAccessor.wrap(result).getUser());
    }

    @Test
    void disabledAccountCannotReconnectToNotificationsWithAnExistingToken() {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("disabled");
        when(userDetailsService.loadUserByUsername("disabled"))
                .thenThrow(new AccountDisabledAuthenticationException());

        assertThrows(
                AccountDisabledAuthenticationException.class,
                () -> interceptor.preSend(
                        message(SimpMessageType.CONNECT, null, "Bearer valid-token", null),
                        channel));
    }

    @Test
    void actorWithoutReportPermissionCannotSubscribeToAdminTopic() {
        Message<byte[]> message = message(
                SimpMessageType.SUBSCRIBE,
                "/topic/admin/notifications",
                null,
                authentication(customer()));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void staffWithReportViewCanSubscribeToAdminTopic() {
        Message<byte[]> message = message(
                SimpMessageType.SUBSCRIBE,
                "/topic/admin/notifications",
                null,
                authentication(staff(ActionCode.VIEW)));

        assertDoesNotThrow(() -> interceptor.preSend(message, channel));
    }

    @Test
    void authenticatedUserCanOnlySubscribeToStandardPersonalQueue() {
        Message<byte[]> allowed = message(
                SimpMessageType.SUBSCRIBE,
                "/user/queue/notifications",
                null,
                authentication(customer()));
        Message<byte[]> forged = message(
                SimpMessageType.SUBSCRIBE,
                "/user/42/queue/notifications",
                null,
                authentication(customer()));

        assertDoesNotThrow(() -> interceptor.preSend(allowed, channel));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(forged, channel));
    }

    @Test
    void clientsCannotPublishNotificationMessages() {
        Message<byte[]> message = message(
                SimpMessageType.MESSAGE,
                "/app/notifications/send",
                null,
                authentication(staff(ActionCode.VIEW)));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    private Message<byte[]> message(
            SimpMessageType type,
            String destination,
            String authorization,
            UsernamePasswordAuthenticationToken user) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setSessionAttributes(new HashMap<>(Map.of(
                NotificationHandshakeInterceptor.NOTIFICATION_SESSION_ATTRIBUTE, true)));
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
        return user(Map.of(), "CUSTOMER");
    }

    private CustomUserDetails staff(int reportMask) {
        return user(Map.of(FunctionCode.REPORT, reportMask), "STAFF");
    }

    private CustomUserDetails user(Map<FunctionCode, Integer> masks, String authority) {
        return new CustomUserDetails(
                authority.toLowerCase(),
                "hash",
                Set.of(new SimpleGrantedAuthority(authority)),
                masks,
                42L,
                null,
                Map.of());
    }
}

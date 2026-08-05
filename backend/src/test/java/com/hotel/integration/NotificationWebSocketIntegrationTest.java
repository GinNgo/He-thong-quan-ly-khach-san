package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:notificationws;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@ActiveProfiles("test")
class NotificationWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void configurePrincipal() {
        when(userDetailsService.loadUserByUsername("notification-admin")).thenReturn(adminDetails());
    }

    @AfterEach
    void stopClient() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void adminCanConnectAndSubscribeToProtectedNotificationTopic() throws Exception {
        SockJsClient sockJsClient = new SockJsClient(List.of(
                new WebSocketTransport(new StandardWebSocketClient())));
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token(adminDetails()));
        CompletableFuture<StompSession> connected = new CompletableFuture<>();
        CompletableFuture<Throwable> sessionFailure = new CompletableFuture<>();

        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        connected.complete(session);
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        sessionFailure.complete(new IllegalStateException(
                                "STOMP error: " + headers));
                    }

                    @Override
                    public void handleException(
                            StompSession session,
                            StompCommand command,
                            StompHeaders headers,
                            byte[] payload,
                            Throwable exception) {
                        sessionFailure.complete(exception);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        sessionFailure.complete(exception);
                    }
                });

        StompSession session = connected.get(10, TimeUnit.SECONDS);
        session.subscribe(
                "/topic/admin/notifications", new StompSessionHandlerAdapter() {
                });

        Thread.sleep(1500);
        assertFalse(sessionFailure.isDone(),
                () -> "Notification STOMP session failed: " + sessionFailure.getNow(null));
        assertTrue(session.isConnected());
        session.disconnect();
    }

    private String token(CustomUserDetails user) {
        return jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities()));
    }

    private CustomUserDetails adminDetails() {
        return new CustomUserDetails(
                "notification-admin",
                "hash",
                Set.of(new SimpleGrantedAuthority("ADMIN")),
                Map.of(FunctionCode.REPORT, ActionCode.VIEW),
                7L,
                null,
                Map.of());
    }
}

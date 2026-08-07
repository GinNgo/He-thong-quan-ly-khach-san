package com.hotel.integration;

import com.hotel.BackendApplication;
<<<<<<< HEAD
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtTokenProvider;
=======
import com.hotel.dtos.AuthResponse;
import com.hotel.dtos.LoginRequest;
import com.hotel.services.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
>>>>>>> codex/ui-functional-audit-polish
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
<<<<<<< HEAD
import org.springframework.boot.test.mock.mockito.MockBean;
=======
import org.springframework.beans.factory.annotation.Value;
>>>>>>> codex/ui-functional-audit-polish
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

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
<<<<<<< HEAD
import java.util.Set;
=======
>>>>>>> codex/ui-functional-audit-polish
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

<<<<<<< HEAD
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
=======
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
>>>>>>> codex/ui-functional-audit-polish

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

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @BeforeEach
    void configurePrincipal() {
        when(userDetailsService.loadUserByUsername("notification-admin")).thenReturn(adminDetails());
    }

    @AfterEach
    void stopClients() {
        clients.forEach(WebSocketStompClient::stop);
        clients.clear();
    }

    @Test
    void expiredBearerTokenCannotEstablishNotificationSession() throws Exception {
        SessionProbe probe = connect(expiredToken("e2e-test-admin"));

        probe.awaitFailure();
        assertThat(probe.connected().isDone()).isFalse();
    }

    @Test
    void adminCanConnectAndSubscribeToProtectedNotificationTopic() throws Exception {
<<<<<<< HEAD
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
=======
        SessionProbe probe = connect(adminToken());
        StompSession session = probe.awaitConnected();

        session.subscribe("/topic/admin/notifications", new StompSessionHandlerAdapter() {
        });

        probe.assertStable();
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    void customerCanUsePersonalQueueButCannotSubscribeToAdminTopic() throws Exception {
        SessionProbe personalProbe = connect(customerToken());
        StompSession personalSession = personalProbe.awaitConnected();
        personalSession.subscribe("/user/queue/notifications", new StompSessionHandlerAdapter() {
        });
        personalProbe.assertStable();
        personalSession.disconnect();

        SessionProbe adminTopicProbe = connect(customerToken());
        StompSession adminTopicSession = adminTopicProbe.awaitConnected();
        adminTopicSession.subscribe("/topic/admin/notifications", new StompSessionHandlerAdapter() {
        });

        adminTopicProbe.awaitFailure();
        awaitDisconnected(adminTopicSession);
    }

    @Test
    void notificationClientsCannotPublishApplicationMessages() throws Exception {
        SessionProbe probe = connect(adminToken());
        StompSession session = probe.awaitConnected();

        session.send("/app/notifications/send", Map.of("message", "forged"));

        probe.awaitFailure();
        awaitDisconnected(session);
    }

    private SessionProbe connect(String token) {
        WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(List.of(
                new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        clients.add(client);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        SessionProbe probe = new SessionProbe();
        client.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        probe)
                .whenComplete((session, error) -> {
                    if (error != null) {
                        probe.failure().complete(error);
                    } else {
                        probe.connected().complete(session);
                    }
                });
        return probe;
    }

    private String adminToken() {
        return login("e2e-test-admin", "admin-test-password");
    }

    private String customerToken() {
        return login("e2e-test-customer", "customer-test-password");
    }

    private String login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        AuthResponse response = authService.login(request);
        return response.getAccessToken();
    }

    private String expiredToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(Date.from(now.minusSeconds(120)))
                .setExpiration(Date.from(now.minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private void awaitDisconnected(StompSession session) throws InterruptedException {
        for (int attempt = 0; attempt < 20 && session.isConnected(); attempt++) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).isFalse();
    }

    private static final class SessionProbe extends StompSessionHandlerAdapter {

        private final CompletableFuture<StompSession> connected = new CompletableFuture<>();
        private final CompletableFuture<Throwable> failure = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            connected.complete(session);
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            failure.complete(new IllegalStateException("STOMP error: " + headers));
        }

        @Override
        public void handleException(
                StompSession session,
                StompCommand command,
                StompHeaders headers,
                byte[] payload,
                Throwable exception) {
            failure.complete(exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            failure.complete(exception);
        }

        StompSession awaitConnected() throws Exception {
            return connected.get(10, TimeUnit.SECONDS);
        }

        Throwable awaitFailure() throws Exception {
            return failure.get(10, TimeUnit.SECONDS);
        }

        void assertStable() {
            assertThatThrownBy(() -> failure.get(750, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        }

        CompletableFuture<StompSession> connected() {
            return connected;
        }

        CompletableFuture<Throwable> failure() {
            return failure;
        }
>>>>>>> codex/ui-functional-audit-polish
    }
}

package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.dtos.AuthResponse;
import com.hotel.dtos.LoginRequest;
import com.hotel.services.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:notificationws;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.e2e-fixtures.enabled=true",
        "payment.property.encryption-key=test-property-payment-encryption-key",
        "LUXESTAY_E2E_CUSTOMER_USERNAME=e2e-test-customer",
        "LUXESTAY_E2E_CUSTOMER_PASSWORD=customer-test-password",
        "LUXESTAY_E2E_ADMIN_USERNAME=e2e-test-admin",
        "LUXESTAY_E2E_ADMIN_PASSWORD=admin-test-password",
        "LUXESTAY_E2E_OWNER_USERNAME=e2e-test-owner",
        "LUXESTAY_E2E_OWNER_PASSWORD=owner-test-password"
})
@ActiveProfiles("test")
class NotificationWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AuthService authService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final List<WebSocketStompClient> clients = new ArrayList<>();

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
    }
}

package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.dtos.AuthResponse;
import com.hotel.dtos.LoginRequest;
import com.hotel.services.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:chatws;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.e2e-fixtures.enabled=true",
        "payment.property.encryption-key=test-property-payment-encryption-key",
        "LUXESTAY_E2E_CUSTOMER_USERNAME=e2e-chat-customer",
        "LUXESTAY_E2E_CUSTOMER_PASSWORD=customer-chat-password",
        "LUXESTAY_E2E_ADMIN_USERNAME=e2e-chat-admin",
        "LUXESTAY_E2E_ADMIN_PASSWORD=admin-chat-password",
        "LUXESTAY_E2E_OWNER_USERNAME=e2e-chat-owner",
        "LUXESTAY_E2E_OWNER_PASSWORD=owner-chat-password"
})
@ActiveProfiles("test")
class ChatWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AuthService authService;

    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach
    void stopClients() {
        clients.forEach(WebSocketStompClient::stop);
        clients.clear();
    }

    @Test
    void invalidBearerTokenCannotEstablishAChatStompSession() throws Exception {
        SessionProbe probe = connect("definitely-invalid-chat-token");

        probe.awaitFailure();
        assertThat(probe.connected().isDone()).isFalse();
    }

    @Test
    void chatSessionIsDisconnectedWhenItSubscribesToAnUnapprovedDestination() throws Exception {
        SessionProbe probe = connect(customerToken());
        StompSession session = probe.awaitConnected();

        session.subscribe("/topic/admin/notifications", new StompSessionHandlerAdapter() {
        });

        probe.awaitFailure();
        awaitDisconnected(session);
    }

    @Test
    void customerCannotSubscribeToSupportQueueButSupportCan() throws Exception {
        SessionProbe customerProbe = connect(customerToken());
        StompSession customerSession = customerProbe.awaitConnected();
        customerSession.subscribe("/user/queue/support/messages", new StompSessionHandlerAdapter() {
        });
        customerProbe.awaitFailure();
        awaitDisconnected(customerSession);

        SessionProbe supportProbe = connect(adminToken());
        StompSession supportSession = supportProbe.awaitConnected();
        supportSession.subscribe("/user/queue/support/messages", new StompSessionHandlerAdapter() {
        });
        supportProbe.assertStable();
        assertThat(supportSession.isConnected()).isTrue();
        supportSession.disconnect();
    }

    @Test
    void customerCanDisconnectAndReconnectWithAReauthenticatedSession() throws Exception {
        String token = customerToken();
        SessionProbe firstProbe = connect(token);
        StompSession firstSession = firstProbe.awaitConnected();
        firstSession.subscribe("/user/queue/messages", new StompSessionHandlerAdapter() {
        });
        firstProbe.assertStable();
        firstSession.disconnect();
        awaitDisconnected(firstSession);

        SessionProbe reconnectProbe = connect(token);
        StompSession reconnected = reconnectProbe.awaitConnected();
        reconnected.subscribe("/user/queue/messages", new StompSessionHandlerAdapter() {
        });
        reconnectProbe.assertStable();
        assertThat(reconnected.isConnected()).isTrue();
        reconnected.disconnect();
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
                        "ws://localhost:" + port + "/ws-chat",
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

    private String customerToken() {
        return login("e2e-chat-customer", "customer-chat-password");
    }

    private String adminToken() {
        return login("e2e-chat-admin", "admin-chat-password");
    }

    private String login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        AuthResponse response = authService.login(request);
        return response.getAccessToken();
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

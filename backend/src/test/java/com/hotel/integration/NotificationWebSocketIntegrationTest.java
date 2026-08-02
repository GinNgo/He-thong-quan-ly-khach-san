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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private WebSocketStompClient stompClient;

    @AfterEach
    void stopClient() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void adminCanConnectAndSubscribeToProtectedNotificationTopic() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("e2e-test-admin");
        request.setPassword("admin-test-password");
        AuthResponse response = authService.login(request);

        SockJsClient sockJsClient = new SockJsClient(List.of(
                new WebSocketTransport(new StandardWebSocketClient())));
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + response.getAccessToken());
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
}

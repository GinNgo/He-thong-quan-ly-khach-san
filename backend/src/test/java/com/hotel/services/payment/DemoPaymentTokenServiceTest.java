package com.hotel.services.payment;

import com.hotel.entities.PaymentSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoPaymentTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T04:00:00Z");

    @Test
    void issuedTokenRoundTripsAndTamperingIsRejected() {
        DemoPaymentTokenService service = new DemoPaymentTokenService(
                "test-signing-secret-with-at-least-32-bytes",
                Clock.fixed(NOW, ZoneOffset.UTC));
        PaymentSession session = session(NOW.plusSeconds(600));

        String token = service.issue(session);

        VerifiedDemoToken verified = service.verify(token);
        assertEquals("session-123", verified.sessionId());
        assertEquals(NOW.plusSeconds(600), verified.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> service.verify(token + "x"));
    }

    @Test
    void expiredTokenIsRejected() {
        DemoPaymentTokenService service = new DemoPaymentTokenService(
                "test-signing-secret-with-at-least-32-bytes",
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class, () -> service.issue(session(NOW.minusSeconds(1))));
    }

    private PaymentSession session(Instant expiry) {
        PaymentSession session = new PaymentSession();
        session.setPublicId("session-123");
        session.setExpiresAt(LocalDateTime.ofInstant(expiry, ZoneOffset.UTC));
        return session;
    }
}

package com.hotel.services.payment;

import com.hotel.entities.PaymentSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

@Service
public class DemoPaymentTokenService {

    private final byte[] signingKey;
    private final Clock clock;

    public DemoPaymentTokenService(
            @Value("${payment.demo.signing-secret:${jwt.secret}}") String signingSecret,
            Clock clock) {
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new IllegalArgumentException("Demo payment signing secret must contain at least 32 characters.");
        }
        this.signingKey = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String issue(PaymentSession session) {
        if (session == null || session.getPublicId() == null || session.getExpiresAt() == null) {
            throw new IllegalArgumentException("Payment session is incomplete.");
        }
        Instant expiresAt = toInstant(session.getExpiresAt());
        if (!expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Payment session has expired.");
        }
        String claims = session.getPublicId() + "." + expiresAt.getEpochSecond();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        return payload + "." + sign(payload);
    }

    public VerifiedDemoToken verify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Payment token is required.");
        }
        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Payment token is invalid.");
        }
        byte[] expected = sign(parts[0]).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = parts[1].getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("Payment token signature is invalid.");
        }

        String claims;
        try {
            claims = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Payment token payload is invalid.", exception);
        }
        int separator = claims.lastIndexOf('.');
        if (separator <= 0 || separator == claims.length() - 1) {
            throw new IllegalArgumentException("Payment token payload is invalid.");
        }
        String sessionId = claims.substring(0, separator);
        Instant expiresAt;
        try {
            expiresAt = Instant.ofEpochSecond(Long.parseLong(claims.substring(separator + 1)));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Payment token expiry is invalid.", exception);
        }
        if (!expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Payment token has expired.");
        }
        return new VerifiedDemoToken(sessionId, expiresAt);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign demo payment token.", exception);
        }
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        ZoneId zone = clock.getZone();
        return value.atZone(zone).toInstant();
    }
}

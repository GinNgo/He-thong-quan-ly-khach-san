package com.hotel.services.payment;

import java.time.Instant;

public record VerifiedDemoToken(String sessionId, Instant expiresAt) {
}

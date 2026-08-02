package com.hotel.domain.lifecycle;

import java.util.Locale;

public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    EXPIRED;

    public static PaymentStatus fromStorage(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "SUCCESS", "PAID", "COMPLETED", "REFUNDED" -> SUCCEEDED;
            case "PENDING_PAYMENT", "PROCESSING" -> PENDING;
            default -> parse(normalized);
        };
    }

    private static PaymentStatus parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported payment status: " + value, exception);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payment status is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

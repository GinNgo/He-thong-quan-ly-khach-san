package com.hotel.domain.lifecycle;

import java.util.Locale;

public enum RefundStatus {
    REQUESTED,
    PENDING_PROVIDER,
    SUCCEEDED,
    FAILED;

    public static RefundStatus fromStorage(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "PENDING", "PENDING_REFUND" -> PENDING_PROVIDER;
            case "SUCCESS", "COMPLETED", "REFUNDED" -> SUCCEEDED;
            default -> parse(normalized);
        };
    }

    private static RefundStatus parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported refund status: " + value, exception);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refund status is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

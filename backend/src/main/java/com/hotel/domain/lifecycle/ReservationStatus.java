package com.hotel.domain.lifecycle;

import java.util.Locale;

public enum ReservationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    CHECKED_IN,
    CHECKED_OUT,
    COMPLETED,
    REJECTED,
    NO_SHOW;

    public static ReservationStatus fromStorage(String value) {
        String normalized = normalize(value);
        if ("PENDING".equals(normalized)) {
            return PENDING_PAYMENT;
        }
        return parse(normalized, "reservation");
    }

    private static ReservationStatus parse(String value, String aggregate) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported " + aggregate + " status: " + value, exception);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Reservation status is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

package com.hotel.domain.payment;

import java.util.Locale;

public enum PaymentProvider {
    VNPAY,
    MOMO,
    ZALOPAY;

    public static PaymentProvider fromRequest(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payment provider is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported payment provider: " + value, exception);
        }
    }
}

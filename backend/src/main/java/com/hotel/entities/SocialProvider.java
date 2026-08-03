package com.hotel.entities;

import java.util.Locale;

public enum SocialProvider {
    GOOGLE,
    FACEBOOK;

    public static SocialProvider fromPath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Social provider is required.");
        }
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}

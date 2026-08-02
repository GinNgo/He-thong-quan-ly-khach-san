package com.hotel.exceptions;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIdSupport {
    public static final String HEADER = "X-Correlation-ID";
    private static final int MAX_LENGTH = 100;
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._:-]+");

    private CorrelationIdSupport() {
    }

    public static String resolve(HttpServletRequest request) {
        String supplied = request.getHeader(HEADER);
        if (supplied == null || supplied.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = UNSAFE.matcher(supplied.trim()).replaceAll("-");
        if (normalized.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return normalized.substring(0, Math.min(MAX_LENGTH, normalized.length()));
    }
}

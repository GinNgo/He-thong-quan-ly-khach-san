package com.hotel.exceptions;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIdSupport {
    public static final String HEADER = "X-Correlation-ID";
    public static final String ATTRIBUTE = CorrelationIdSupport.class.getName() + ".correlationId";
    private static final int MAX_LENGTH = 100;
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._:-]+");

    private CorrelationIdSupport() {
    }

    public static String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String correlationId && !correlationId.isBlank()) {
            return correlationId;
        }
        String correlationId = normalize(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        return correlationId;
    }

    public static String normalize(String supplied) {
        if (supplied == null || supplied.isBlank()) {
            return generate();
        }
        String normalized = UNSAFE.matcher(supplied.trim()).replaceAll("-");
        if (normalized.isBlank()) {
            return generate();
        }
        return normalized.substring(0, Math.min(MAX_LENGTH, normalized.length()));
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}

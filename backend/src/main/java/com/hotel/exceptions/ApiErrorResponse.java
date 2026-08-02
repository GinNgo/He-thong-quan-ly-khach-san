package com.hotel.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable error envelope shared by controller, validation, security and financial failures. */
public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String correlationId,
        Map<String, String> fieldErrors,
        boolean retryable,
        String currentState,
        String path) {

    public ApiErrorResponse {
        fieldErrors = fieldErrors == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(fieldErrors));
    }
}

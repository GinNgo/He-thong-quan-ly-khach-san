package com.hotel.paymentprovider.error;

import java.util.Map;

public record FinancialErrorResponse(
        String code,
        String message,
        String correlationId,
        Map<String, String> fieldErrors,
        boolean retryable,
        String currentState) {
}

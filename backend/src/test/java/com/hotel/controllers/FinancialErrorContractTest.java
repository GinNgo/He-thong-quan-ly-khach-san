package com.hotel.controllers;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.paymentprovider.error.FinancialException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialErrorContractTest {

    @Test
    void financialErrorsUseStableSafeShapeAndCorrelation() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        request.addHeader("X-Correlation-ID", "test-correlation");
        FinancialException exception = new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED,
                FinancialErrorCode.IDEMPOTENCY_KEY_REUSED.defaultMessage(), null, null,
                new IllegalStateException("secret=hidden"));

        ApiErrorResponse response = handler.handleFinancial(exception, request).getBody();

        assertEquals(409, response.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", response.code());
        assertEquals("test-correlation", response.correlationId());
        assertFalse(response.retryable());
        assertFalse(response.message().contains("secret=hidden"));
        assertEquals("/api/payments", response.path());
    }

    @Test
    void correlationIdsAreSanitizedAndBounded() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("X-Correlation-ID", "prefix / unsafe " + "x".repeat(150));

        ApiErrorResponse response = handler.handleBadRequest(
                new IllegalArgumentException("Invalid"), request).getBody();

        assertTrue(response.correlationId().length() <= 100);
        assertTrue(response.correlationId().matches("[A-Za-z0-9._:-]+"));
    }
}

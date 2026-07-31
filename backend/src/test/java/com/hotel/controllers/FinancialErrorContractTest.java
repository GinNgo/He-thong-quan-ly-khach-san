package com.hotel.controllers;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialErrorResponse;
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

        FinancialErrorResponse response = handler.handleFinancial(exception, request).getBody();

        assertEquals("IDEMPOTENCY_KEY_REUSED", response.code());
        assertEquals("test-correlation", response.correlationId());
        assertFalse(response.retryable());
        assertFalse(response.message().contains("secret=hidden"));
    }
}

package com.hotel.controllers;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.PropertyNotOperationalException;
import com.hotel.exceptions.RegistrationConflictException;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.security.PasswordChangeException;
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

    @Test
    void exceptionMessagesAreNeverReflectedFromGenericOrFinancialFailures() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        String sensitive = "token=secret customer@example.test";

        assertEquals("The amount is invalid.", handler.handleFinancial(
                new FinancialException(FinancialErrorCode.INVALID_AMOUNT, sensitive), request).getBody().message());
        assertEquals("The request is invalid.", handler.handleBadRequest(
                new IllegalArgumentException(sensitive), request).getBody().message());
        assertEquals("The request conflicts with current state.", handler.handleConflict(
                new IllegalStateException(sensitive), request).getBody().message());
        assertEquals("The requested resource was not found.", handler.handleNotFound(
                new ResourceNotFoundException(sensitive), request).getBody().message());
    }

    @Test
    void domainHandlersUseAllowListedMessages() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");

        assertEquals(PropertyNotOperationalException.DEFAULT_MESSAGE, handler.handlePropertyNotOperational(
                new PropertyNotOperationalException("PENDING", "INACTIVE"), request).getBody().message());
        assertEquals("An account with this username already exists.", handler.handleRegistrationConflict(
                RegistrationConflictException.username(), request).getBody().message());
        assertEquals("An account with this email already exists.", handler.handleRegistrationConflict(
                RegistrationConflictException.email(), request).getBody().message());
        assertEquals("The current password is incorrect.", handler.handlePasswordChange(
                PasswordChangeException.currentPasswordInvalid(), request).getBody().message());
    }
}

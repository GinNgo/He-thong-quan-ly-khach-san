package com.hotel.controllers;

import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.PropertyNotOperationalException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PropertyOperationalErrorContractTest {

    @Test
    void propertyStateGateUsesStableConflictContract() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/management/rooms");

        ApiErrorResponse response = handler.handlePropertyNotOperational(
                new PropertyNotOperationalException("PENDING_APPROVAL", "INACTIVE"), request).getBody();

        assertEquals(409, response.status());
        assertEquals("PROPERTY_NOT_OPERATIONAL", response.code());
        assertEquals("approval=PENDING_APPROVAL;operation=INACTIVE", response.currentState());
        assertFalse(response.retryable());
    }
}

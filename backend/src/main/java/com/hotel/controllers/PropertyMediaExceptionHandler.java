package com.hotel.controllers;

import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.exceptions.PropertyMediaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PropertyMediaExceptionHandler {

    @ExceptionHandler(PropertyMediaException.class)
    public ResponseEntity<ApiErrorResponse> handle(
            PropertyMediaException exception,
            HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        ApiErrorResponse body = new ApiErrorResponse(
                exception.status().value(),
                exception.code(),
                exception.getMessage(),
                correlationId,
                Map.of(),
                exception.retryable(),
                null,
                request.getRequestURI());
        return ResponseEntity.status(exception.status())
                .header(CorrelationIdSupport.HEADER, correlationId)
                .body(body);
    }
}

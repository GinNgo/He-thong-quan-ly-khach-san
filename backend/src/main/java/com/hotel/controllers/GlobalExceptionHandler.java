package com.hotel.controllers;

import com.hotel.paymentprovider.error.FinancialErrorResponse;
import com.hotel.paymentprovider.error.FinancialException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hotel.exceptions.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FinancialException.class)
    public ResponseEntity<FinancialErrorResponse> handleFinancial(FinancialException ex, HttpServletRequest request) {
        String correlationId = correlationId(request);
        // Log only identifiers; provider payloads, credentials and exception messages stay out of logs.
        log.warn("Financial request rejected code={} path={} correlationId={}",
                ex.code().name(), request.getRequestURI(), correlationId);
        FinancialErrorResponse body = new FinancialErrorResponse(
                ex.code().name(), ex.getMessage(), correlationId, ex.fieldErrors(),
                ex.code().retryable(), ex.currentState());
        return ResponseEntity.status(ex.code().status()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Thông tin gửi lên chưa hợp lệ."));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "code", "NOT_FOUND",
                "message", ex.getMessage(),
                "path", request.getRequestURI()));
    }

    private String correlationId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Correlation-ID");
        if (supplied == null || supplied.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return supplied.replaceAll("[^A-Za-z0-9._:-]", "-").substring(0, Math.min(100, supplied.length()));
    }
}

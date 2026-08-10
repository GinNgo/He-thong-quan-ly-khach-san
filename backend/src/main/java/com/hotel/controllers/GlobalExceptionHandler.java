package com.hotel.controllers;

import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.AiServiceUnavailableException;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.exceptions.PropertyNotOperationalException;
import com.hotel.exceptions.RegistrationConflictException;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.paymentprovider.error.FinancialException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import com.hotel.security.AccountDisabledAuthenticationException;
import com.hotel.security.PasswordChangeException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FinancialException.class)
    public ResponseEntity<ApiErrorResponse> handleFinancial(
            FinancialException ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        log.warn("Financial request rejected code={} path={} correlationId={}",
                ex.code().name(), request.getRequestURI(), correlationId);
        return response(ex.code().status(), ex.code().name(), ex.code().defaultMessage(), request,
                ex.fieldErrors(), ex.code().retryable(), ex.currentState(), correlationId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fieldErrors.putIfAbsent(
                error.getField(),
                error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.",
                request, fieldErrors, false, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> fieldErrors.putIfAbsent(
                violation.getPropertyPath().toString(), violation.getMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.",
                request, fieldErrors, false, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is malformed.",
                request, Map.of(), false, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "A required request parameter is missing.",
                request, Map.of(ex.getParameterName(), "Required parameter is missing."), false, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "A required request header is missing.",
                request, Map.of(ex.getHeaderName(), "Required header is missing."), false, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "A request parameter has an invalid value.",
                request, Map.of(ex.getName(), "Invalid parameter value."), false, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid.",
                request, Map.of(), false, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            IllegalStateException ex,
            HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with current state.",
                request, Map.of(), false, null);
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleAiUnavailable(
            AiServiceUnavailableException ex,
            HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", ex.getMessage(),
                request, Map.of(), true, null);
    }

    @ExceptionHandler(PropertyNotOperationalException.class)
    public ResponseEntity<ApiErrorResponse> handlePropertyNotOperational(
            PropertyNotOperationalException ex,
            HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, PropertyNotOperationalException.ERROR_CODE,
                PropertyNotOperationalException.DEFAULT_MESSAGE, request, Map.of(), false, ex.currentState());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentModification(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource changed concurrently; reload current state before retrying.",
                request, Map.of(), true, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data.", request, Map.of(), false, null);
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationConflict(
            RegistrationConflictException ex,
            HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, ex.code(), registrationConflictMessage(ex.code()), request,
                ex.fieldErrors(), false, null);
    }

    @ExceptionHandler(PasswordChangeException.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordChange(
            PasswordChangeException ex,
            HttpServletRequest request) {
        return response(ex.getStatus(), ex.getCode(), "The current password is incorrect.", request,
                Map.of(), false, null);
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<ApiErrorResponse> handleForbidden(
            RuntimeException ex,
            HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Bạn không có quyền thực hiện thao tác này.",
                request, Map.of(), false, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {
        if (AccountDisabledAuthenticationException.causedByAccountDisabled(ex)) {
            return response(
                    HttpStatus.UNAUTHORIZED,
                    AccountDisabledAuthenticationException.ERROR_CODE,
                    AccountDisabledAuthenticationException.DEFAULT_MESSAGE,
                    request,
                    Map.of(),
                    false,
                    null);
        }
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Full authentication is required to access this resource",
                request, Map.of(), false, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.",
                request, Map.of(), false, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "The HTTP method is not supported for this endpoint.", request, Map.of(), false, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "The request content type is not supported.", request, Map.of(), false, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        log.error("Unhandled request failure type={} path={} correlationId={}",
                ex.getClass().getSimpleName(), request.getRequestURI(), correlationId);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.",
                request, Map.of(), false, null, correlationId);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors,
            boolean retryable,
            String currentState) {
        return response(status, code, message, request, fieldErrors, retryable, currentState,
                CorrelationIdSupport.resolve(request));
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors,
            boolean retryable,
            String currentState,
            String correlationId) {
        ApiErrorResponse body = new ApiErrorResponse(
                status.value(), code, message, correlationId, fieldErrors, retryable,
                currentState, request.getRequestURI());
        return ResponseEntity.status(status)
                .header(CorrelationIdSupport.HEADER, correlationId)
                .body(body);
    }

    private String registrationConflictMessage(String code) {
        return switch (code) {
            case RegistrationConflictException.USERNAME_CODE -> "An account with this username already exists.";
            case RegistrationConflictException.EMAIL_CODE -> "An account with this email already exists.";
            default -> "The registration request conflicts with an existing account.";
        };
    }
}

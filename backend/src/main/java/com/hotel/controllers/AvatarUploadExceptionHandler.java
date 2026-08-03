package com.hotel.controllers;

import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.AvatarUploadException;
import com.hotel.exceptions.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AvatarUploadExceptionHandler {

    @ExceptionHandler(AvatarUploadException.class)
    public ResponseEntity<ApiErrorResponse> handleAvatarUpload(
            AvatarUploadException exception,
            HttpServletRequest request) {
        return response(
                exception.status(),
                exception.code(),
                exception.publicMessage(),
                exception.retryable(),
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartLimit(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "AVATAR_FILE_TOO_LARGE",
                "The profile image must not exceed 5 MB.",
                false,
                request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        ApiErrorResponse body = new ApiErrorResponse(
                status.value(),
                code,
                message,
                correlationId,
                Map.of(),
                retryable,
                null,
                request.getRequestURI());
        return ResponseEntity.status(status)
                .header(CorrelationIdSupport.HEADER, correlationId)
                .body(body);
    }
}

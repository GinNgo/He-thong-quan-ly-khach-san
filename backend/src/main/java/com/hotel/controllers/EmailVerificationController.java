package com.hotel.controllers;

import com.hotel.dtos.EmailChangeRequest;
import com.hotel.dtos.EmailVerificationDispatchResponse;
import com.hotel.dtos.EmailVerificationRequest;
import com.hotel.dtos.EmailVerificationResultResponse;
import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.EmailVerificationException;
import com.hotel.services.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EmailVerificationController {

    private final EmailVerificationService service;

    public EmailVerificationController(EmailVerificationService service) {
        this.service = service;
    }

    @PostMapping("/api/auth/email-verification/confirm")
    @PreAuthorize("permitAll()")
    public ResponseEntity<EmailVerificationResultResponse> confirm(
            @Valid @RequestBody EmailVerificationRequest request) {
        EmailVerificationService.ConfirmationResult result = service.confirm(request.token());
        return ResponseEntity.ok(new EmailVerificationResultResponse(
                result.emailChanged() ? "Email address updated successfully." : "Email address verified successfully.",
                result.emailChanged(),
                result.email()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/api/users/me/email-verification/resend")
    public ResponseEntity<EmailVerificationDispatchResponse> resend(
            Authentication authentication,
            HttpServletRequest request) {
        EmailVerificationService.DispatchResult result = service.resend(userId(authentication), request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dispatchResponse(result));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/api/users/me/email-change")
    public ResponseEntity<EmailVerificationDispatchResponse> requestEmailChange(
            @Valid @RequestBody EmailChangeRequest emailChangeRequest,
            Authentication authentication,
            HttpServletRequest request) {
        EmailVerificationService.DispatchResult result = service.requestEmailChange(
                userId(authentication), emailChangeRequest.newEmail(), request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dispatchResponse(result));
    }

    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<ApiErrorResponse> handleVerificationException(
            EmailVerificationException exception,
            HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        ApiErrorResponse body = new ApiErrorResponse(
                exception.getStatus().value(), exception.getCode(), exception.getMessage(),
                correlationId, Map.of(), false, null, request.getRequestURI());
        return ResponseEntity.status(exception.getStatus())
                .header(CorrelationIdSupport.HEADER, correlationId)
                .body(body);
    }

    private EmailVerificationDispatchResponse dispatchResponse(EmailVerificationService.DispatchResult result) {
        String message = result.alreadyVerified()
                ? "The current email address is already verified."
                : "If delivery is available, a verification link will be sent shortly.";
        return new EmailVerificationDispatchResponse(
                message, result.emailSent(), result.alreadyVerified(), result.pendingEmail());
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw EmailVerificationException.invalidToken();
        }
        return details.getUserId();
    }
}

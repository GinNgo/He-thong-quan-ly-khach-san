package com.hotel.controllers;

import com.hotel.dtos.PropertyClaimRequestDTO;
import com.hotel.dtos.PropertyClaimRejectionRequest;
import com.hotel.dtos.PropertyClaimResponseDTO;
import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.exceptions.PropertyClaimRateLimitException;
import com.hotel.exceptions.PropertyClaimConflictException;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.PropertyClaimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PropertyClaimController {

    private final PropertyClaimService claimService;

    @PostMapping("/properties/{propertyId}/claim")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> requestClaim(
            @PathVariable Long propertyId,
            @Valid @RequestBody PropertyClaimRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        PropertyClaimResponseDTO claim = claimService.requestClaim(
                propertyId,
                principal.getUserId(),
                request);
        return ResponseEntity.ok(claim);
    }

    @ExceptionHandler(PropertyClaimRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimited(
            PropertyClaimRateLimitException exception,
            HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                PropertyClaimRateLimitException.ERROR_CODE,
                PropertyClaimRateLimitException.DEFAULT_MESSAGE,
                correlationId,
                Map.of(),
                true,
                null,
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(CorrelationIdSupport.HEADER, correlationId)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(PropertyClaimConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleClaimConflict(
            PropertyClaimConflictException exception,
            HttpServletRequest request) {
        return conflictResponse(exception.code(), exception.getMessage(), exception.currentState(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleUntranslatedClaimConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return conflictResponse(
                PropertyClaimConflictException.GENERIC_CODE,
                "The property claim changed concurrently. Refresh and try again.",
                null,
                request);
    }

    private ResponseEntity<ApiErrorResponse> conflictResponse(
            String code, String message, String currentState, HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.CONFLICT.value(), code, message, correlationId,
                Map.of(), false, currentState, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(CorrelationIdSupport.HEADER, correlationId)
                .body(body);
    }

    @GetMapping("/admin/property-claims")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_VIEW') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Page<PropertyClaimResponseDTO>> getAllClaims(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(claimService.listClaims(status, pageable));
    }

    @PostMapping("/admin/property-claims/{id}/approve")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_APPROVE') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<?> approveClaim(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        PropertyClaimResponseDTO claim = claimService.approveClaim(id, principal.getUserId());
        return ResponseEntity.ok(claim);
    }

    @PostMapping("/admin/property-claims/{id}/reject")
    @PreAuthorize("hasAuthority('PROPERTY_CLAIM_APPROVE') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<?> rejectClaim(
            @PathVariable Long id,
            @Valid @RequestBody PropertyClaimRejectionRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        PropertyClaimResponseDTO claim = claimService.rejectClaim(
                id, principal.getUserId(), request.reason());
        return ResponseEntity.ok(claim);
    }

    @PostMapping("/property-claims/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancelClaim(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(claimService.cancelClaim(id, principal.getUserId()));
    }
}

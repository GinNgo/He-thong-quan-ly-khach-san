package com.hotel.controllers;

import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.exceptions.OwnershipLifecycleException;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.PropertyOwnershipGovernanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PropertyOwnershipController {
    private final PropertyOwnershipGovernanceService service;

    @GetMapping("/properties/{propertyId}/owners") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> owners(@PathVariable Long propertyId, @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.owners(propertyId, actor.getUserId()));
    }

    @PostMapping("/properties/{propertyId}/owner-invitations") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> invite(@PathVariable Long propertyId, @Valid @RequestBody InviteRequest request,
                                    @AuthenticationPrincipal CustomUserDetails actor) {
        var result = service.invite(propertyId, actor.getUserId(), request.email());
        return ResponseEntity.ok(new InvitationResponse(result.invitationId(), result.email(), result.status(), result.expiresAt()));
    }

    @PostMapping("/owner-invitations/accept") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> acceptInvite(@Valid @RequestBody AcceptInvitationRequest request,
                                          @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.acceptInvitation(actor.getUserId(), request.token(), request.ownerTermsAccepted()));
    }

    @DeleteMapping("/properties/{propertyId}/owner-invitations/{invitationId}") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelInvite(@PathVariable Long propertyId, @PathVariable Long invitationId,
                                             @AuthenticationPrincipal CustomUserDetails actor) {
        service.cancelInvitation(propertyId, invitationId, actor.getUserId()); return ResponseEntity.noContent().build();
    }

    @PostMapping("/properties/{propertyId}/ownership-transfers") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> transfer(@PathVariable Long propertyId, @Valid @RequestBody TransferRequest request,
                                      @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.initiateTransfer(propertyId, actor.getUserId(), request.targetUserId(), request.currentPassword()));
    }

    @PostMapping("/ownership-transfers/{transferId}/accept") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> acceptTransfer(@PathVariable Long transferId, @RequestBody AcceptTransferRequest request,
                                            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.acceptTransfer(transferId, actor.getUserId(), request.responsibilityAccepted()));
    }

    @DeleteMapping("/ownership-transfers/{transferId}") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelTransfer(@PathVariable Long transferId, @AuthenticationPrincipal CustomUserDetails actor) {
        service.cancelTransfer(transferId, actor.getUserId()); return ResponseEntity.noContent().build();
    }

    @PostMapping("/properties/{propertyId}/owners/leave") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leave(@PathVariable Long propertyId, @Valid @RequestBody ReasonRequest request,
                                      @AuthenticationPrincipal CustomUserDetails actor) {
        service.leave(propertyId, actor.getUserId(), request.reason()); return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/properties/{propertyId}/owners/{userId}") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> remove(@PathVariable Long propertyId, @PathVariable Long userId,
                                       @Valid @RequestBody ReasonRequest request, @AuthenticationPrincipal CustomUserDetails actor) {
        service.remove(propertyId, actor.getUserId(), userId, request.reason()); return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(OwnershipLifecycleException.class)
    public ResponseEntity<ApiErrorResponse> ownershipConflict(OwnershipLifecycleException exception, HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        HttpStatus status = "OWNERSHIP_NOT_FOUND".equals(exception.code()) ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).header(CorrelationIdSupport.HEADER, correlationId).body(new ApiErrorResponse(
                status.value(), exception.code(), exception.getMessage(), correlationId, Map.of(), false, null, request.getRequestURI()));
    }

    public record InviteRequest(@NotBlank @Email String email) {}
    public record AcceptInvitationRequest(@NotBlank String token, boolean ownerTermsAccepted) {}
    public record TransferRequest(@NotNull Long targetUserId, @NotBlank String currentPassword) {}
    public record AcceptTransferRequest(boolean responsibilityAccepted) {}
    public record ReasonRequest(@NotBlank String reason) {}
    public record InvitationResponse(Long invitationId, String email, String status, LocalDateTime expiresAt) {}
}

package com.hotel.controllers;

import com.hotel.dtos.AuthResponse;
import com.hotel.dtos.LoginRequest;
import com.hotel.dtos.GoogleLoginRequest;
import com.hotel.dtos.FacebookLoginRequest;
import com.hotel.dtos.PasswordResetCompletionRequest;
import com.hotel.dtos.PasswordResetRequest;
import com.hotel.dtos.PasswordResetResponse;
import com.hotel.dtos.RegisterRequest;
import com.hotel.dtos.RegistrationResponse;
import com.hotel.dtos.SocialIdentityLinkRequest;
import com.hotel.dtos.SocialIdentityResponse;
import com.hotel.dtos.SocialIdentityUnlinkRequest;
import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.security.AccountDisabledAuthenticationException;
import com.hotel.security.RefreshTokenException;
import com.hotel.security.PasswordResetException;
import com.hotel.security.LoginTemporarilyBlockedException;
import com.hotel.exceptions.SocialAccountLinkException;
import com.hotel.services.AuthService;
import com.hotel.services.PasswordResetService;
import com.hotel.services.RefreshTokenCookieService;
import com.hotel.services.RefreshTokenService;
import com.hotel.services.LoginSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final com.hotel.services.CredentialRegistrationService credentialRegistrationService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final com.hotel.services.AuthSessionRevocationService authSessionRevocationService;
    private final PasswordResetService passwordResetService;
    private final LoginSecurityService loginSecurityService;

    public AuthController(
            AuthService authService,
            com.hotel.services.CredentialRegistrationService credentialRegistrationService,
            RefreshTokenService refreshTokenService,
            RefreshTokenCookieService refreshTokenCookieService,
            com.hotel.services.AuthSessionRevocationService authSessionRevocationService,
            PasswordResetService passwordResetService,
            LoginSecurityService loginSecurityService) {
        this.authService = authService;
        this.credentialRegistrationService = credentialRegistrationService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenCookieService = refreshTokenCookieService;
        this.authSessionRevocationService = authSessionRevocationService;
        this.passwordResetService = passwordResetService;
        this.loginSecurityService = loginSecurityService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        LoginSecurityService.LoginGuard guard = loginSecurityService.preAuthenticate(
                loginRequest.getUsername(), request.getRemoteAddr(), CorrelationIdSupport.resolve(request));
        try {
            AuthResponse response = authService.login(loginRequest);
            loginSecurityService.recordSuccess(guard, response.getUserId());
            return withRefreshCookie(response, refreshTokenService.issueForUser(response.getUserId()));
        } catch (org.springframework.security.core.AuthenticationException exception) {
            if (!AccountDisabledAuthenticationException.causedByAccountDisabled(exception)) {
                LoginSecurityService.BlockDecision decision = loginSecurityService.recordFailure(guard);
                if (decision.blocked()) {
                    throw new LoginTemporarilyBlockedException(decision.retryAfterSeconds());
                }
            }
            throw exception;
        }
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request.getIdToken());
        return withRefreshCookie(response, refreshTokenService.issueForUser(response.getUserId()));
    }

    @PostMapping("/facebook")
    public ResponseEntity<AuthResponse> loginWithFacebook(@Valid @RequestBody FacebookLoginRequest request) {
        AuthResponse response = authService.loginWithFacebook(request.getAccessToken());
        return withRefreshCookie(response, refreshTokenService.issueForUser(response.getUserId()));
    }

    @GetMapping("/social-identities")
    public ResponseEntity<java.util.List<SocialIdentityResponse>> listSocialIdentities(
            Authentication authentication) {
        return ResponseEntity.ok(authService.listSocialIdentities(requireAuthenticatedUserId(authentication)));
    }

    @PostMapping("/social-identities/{provider}/link")
    public ResponseEntity<SocialIdentityResponse> linkSocialIdentity(
            @PathVariable String provider,
            @Valid @RequestBody SocialIdentityLinkRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(authService.linkSocialIdentity(
                requireAuthenticatedUserId(authentication), provider, request.getCredential()));
    }

    @DeleteMapping("/social-identities/{provider}")
    public ResponseEntity<Void> unlinkSocialIdentity(
            @PathVariable String provider,
            @RequestBody(required = false) SocialIdentityUnlinkRequest request,
            Authentication authentication) {
        authService.unlinkSocialIdentity(
                requireAuthenticatedUserId(authentication),
                provider,
                request == null ? null : request.getCurrentPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            @RequestHeader(name = "X-Refresh-Request", required = false) String refreshMarker) {
        if (!"1".equals(refreshMarker)) {
            throw RefreshTokenException.invalidRequest();
        }
        RefreshTokenService.RefreshGrant grant = refreshTokenService.rotate(
                refreshTokenCookieService.extract(request));
        return withRefreshCookie(authService.refreshAccessToken(grant.userId()), grant);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            @RequestHeader(name = "X-Logout-Request", required = false) String logoutMarker,
            Authentication authentication) {
        if (!"1".equals(logoutMarker)) {
            throw RefreshTokenException.invalidLogoutRequest();
        }

        Long userId = refreshTokenService
                .revokeByToken(refreshTokenCookieService.extract(request),
                        java.time.Instant.now(), "LOGOUT")
                .orElseGet(() -> authenticatedUserId(authentication));
        if (userId != null) {
            authSessionRevocationService.revokeUserSession(userId, "LOGOUT");
        }

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear())
                .build();
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        return new ResponseEntity<>(credentialRegistrationService.register(registerRequest), HttpStatus.CREATED);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        passwordResetService.requestReset(request.getEmail(), servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new PasswordResetResponse(passwordResetService.genericResponseMessage()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetCompletionRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(PasswordResetException.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordResetException(
            PasswordResetException exception,
            HttpServletRequest request) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            org.springframework.security.core.AuthenticationException ex,
            HttpServletRequest request) {
        if (AccountDisabledAuthenticationException.causedByAccountDisabled(ex)) {
            return error(
                    HttpStatus.UNAUTHORIZED,
                    AccountDisabledAuthenticationException.ERROR_CODE,
                    AccountDisabledAuthenticationException.DEFAULT_MESSAGE,
                    request);
        }
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid username or password.", request);
    }

    @ExceptionHandler(AccountDisabledAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountDisabled(
            AccountDisabledAuthenticationException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.UNAUTHORIZED,
                AccountDisabledAuthenticationException.ERROR_CODE,
                AccountDisabledAuthenticationException.DEFAULT_MESSAGE,
                request);
    }

    @ExceptionHandler(LoginTemporarilyBlockedException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginTemporarilyBlocked(
            LoginTemporarilyBlockedException exception,
            HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = error(
                HttpStatus.TOO_MANY_REQUESTS,
                LoginTemporarilyBlockedException.ERROR_CODE,
                LoginTemporarilyBlockedException.DEFAULT_MESSAGE,
                true,
                request);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshTokenException(
            RefreshTokenException exception,
            HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> response = error(
                exception.getStatus(), exception.getCode(), exception.getMessage(), request);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear())
                .body(response.getBody());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderConfigurationException(
            IllegalStateException exception,
            HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidProviderToken(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_AUTH_TOKEN", exception.getMessage(), request);
    }

    @ExceptionHandler(SocialAccountLinkException.class)
    public ResponseEntity<ApiErrorResponse> handleSocialAccountLinkException(
            SocialAccountLinkException exception,
            HttpServletRequest request) {
        return error(exception.status(), exception.code(), exception.getMessage(), exception.retryable(), request);
    }

    @GetMapping("/my-menu")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<com.hotel.dtos.AppModuleDto>> getMyMenu() {
        return ResponseEntity.ok(authService.getMyMenu());
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(
            AuthResponse response,
            RefreshTokenService.RefreshGrant grant) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookieService.issue(grant.rawToken(), grant.expiresAt()))
                .body(response);
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (authentication.getPrincipal() instanceof com.hotel.security.CustomUserDetails details) {
            return details.getUserId();
        }
        if (authentication.getPrincipal() instanceof UserDetails details) {
            return authSessionRevocationService.findUserId(details.getUsername()).orElse(null);
        }
        return authSessionRevocationService.findUserId(authentication.getName()).orElse(null);
    }

    private Long requireAuthenticatedUserId(Authentication authentication) {
        Long userId = authenticatedUserId(authentication);
        if (userId == null) {
            throw SocialAccountLinkException.authenticationRequired();
        }
        return userId;
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return error(status, code, message, false, request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            HttpServletRequest request) {
        String correlationId = CorrelationIdSupport.resolve(request);
        ApiErrorResponse body = new ApiErrorResponse(
                status.value(), code, message, correlationId, Map.of(), retryable, null, request.getRequestURI());
        return ResponseEntity.status(status)
                .header(CorrelationIdSupport.HEADER, correlationId)
                .body(body);
    }
}

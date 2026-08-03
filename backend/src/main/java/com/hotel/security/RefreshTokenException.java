package com.hotel.security;

import org.springframework.http.HttpStatus;

public class RefreshTokenException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private RefreshTokenException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static RefreshTokenException invalid() {
        return new RefreshTokenException(
                "REFRESH_TOKEN_INVALID",
                "The refresh session is invalid.",
                HttpStatus.UNAUTHORIZED);
    }

    public static RefreshTokenException expired() {
        return new RefreshTokenException(
                "REFRESH_TOKEN_EXPIRED",
                "The refresh session has expired.",
                HttpStatus.UNAUTHORIZED);
    }

    public static RefreshTokenException reused() {
        return new RefreshTokenException(
                "REFRESH_TOKEN_REUSED",
                "Refresh token reuse was detected; the session family was revoked.",
                HttpStatus.UNAUTHORIZED);
    }

    public static RefreshTokenException invalidRequest() {
        return new RefreshTokenException(
                "REFRESH_REQUEST_INVALID",
                "The refresh request is invalid.",
                HttpStatus.BAD_REQUEST);
    }

    public static RefreshTokenException invalidLogoutRequest() {
        return new RefreshTokenException(
                "LOGOUT_REQUEST_INVALID",
                "The logout request is invalid.",
                HttpStatus.BAD_REQUEST);
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}

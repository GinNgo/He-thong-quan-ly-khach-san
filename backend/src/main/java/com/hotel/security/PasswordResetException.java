package com.hotel.security;

import org.springframework.http.HttpStatus;

public class PasswordResetException extends RuntimeException {

    public static final String INVALID_TOKEN_CODE = "PASSWORD_RESET_TOKEN_INVALID";
    public static final String EXPIRED_TOKEN_CODE = "PASSWORD_RESET_TOKEN_EXPIRED";

    private final String code;
    private final HttpStatus status;

    private PasswordResetException(String code, String message) {
        super(message);
        this.code = code;
        this.status = HttpStatus.BAD_REQUEST;
    }

    public static PasswordResetException invalidToken() {
        return new PasswordResetException(INVALID_TOKEN_CODE, "The password reset link is invalid or has already been used.");
    }

    public static PasswordResetException expiredToken() {
        return new PasswordResetException(EXPIRED_TOKEN_CODE, "The password reset link has expired. Request a new link.");
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

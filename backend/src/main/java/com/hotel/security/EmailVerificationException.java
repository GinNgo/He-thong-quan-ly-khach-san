package com.hotel.security;

import org.springframework.http.HttpStatus;

public class EmailVerificationException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private EmailVerificationException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static EmailVerificationException invalidToken() {
        return new EmailVerificationException(
                "EMAIL_VERIFICATION_TOKEN_INVALID",
                "The email verification link is invalid or has already been used.",
                HttpStatus.BAD_REQUEST);
    }

    public static EmailVerificationException expiredToken() {
        return new EmailVerificationException(
                "EMAIL_VERIFICATION_TOKEN_EXPIRED",
                "The email verification link has expired. Request a new link.",
                HttpStatus.BAD_REQUEST);
    }

    public static EmailVerificationException identityConflict() {
        return new EmailVerificationException(
                "EMAIL_IDENTITY_CONFLICT",
                "This email cannot be used for the account.",
                HttpStatus.CONFLICT);
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}

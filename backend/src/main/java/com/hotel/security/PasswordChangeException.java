package com.hotel.security;

import org.springframework.http.HttpStatus;

public class PasswordChangeException extends RuntimeException {

    public static final String CURRENT_PASSWORD_INVALID = "CURRENT_PASSWORD_INVALID";

    private final String code;
    private final HttpStatus status;

    private PasswordChangeException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static PasswordChangeException currentPasswordInvalid() {
        return new PasswordChangeException(
                CURRENT_PASSWORD_INVALID,
                "The current password is incorrect.",
                HttpStatus.BAD_REQUEST);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

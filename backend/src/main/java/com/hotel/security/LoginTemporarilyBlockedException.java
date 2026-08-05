package com.hotel.security;

import org.springframework.security.core.AuthenticationException;

public class LoginTemporarilyBlockedException extends AuthenticationException {

    public static final String ERROR_CODE = "LOGIN_TEMPORARILY_BLOCKED";
    public static final String DEFAULT_MESSAGE = "Login is temporarily blocked. Try again later.";

    private final long retryAfterSeconds;

    public LoginTemporarilyBlockedException(long retryAfterSeconds) {
        super(DEFAULT_MESSAGE);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

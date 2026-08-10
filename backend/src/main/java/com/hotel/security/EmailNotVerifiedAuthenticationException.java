package com.hotel.security;

import org.springframework.security.authentication.DisabledException;

public class EmailNotVerifiedAuthenticationException extends DisabledException {

    public static final String ERROR_CODE = "EMAIL_NOT_VERIFIED";
    public static final String DEFAULT_MESSAGE = "Verify your email address before signing in.";

    public EmailNotVerifiedAuthenticationException() {
        super(DEFAULT_MESSAGE);
    }

    public static boolean causedByUnverifiedEmail(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof EmailNotVerifiedAuthenticationException) return true;
            current = current.getCause();
        }
        return false;
    }
}

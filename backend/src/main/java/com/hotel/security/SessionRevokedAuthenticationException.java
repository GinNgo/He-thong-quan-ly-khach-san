package com.hotel.security;

import org.springframework.security.core.AuthenticationException;

/** Stable authentication failure returned when logout invalidated an access token. */
public class SessionRevokedAuthenticationException extends AuthenticationException {

    public static final String ERROR_CODE = "SESSION_REVOKED";
    public static final String DEFAULT_MESSAGE = "This session has been signed out.";

    public SessionRevokedAuthenticationException() {
        super(DEFAULT_MESSAGE);
    }
}

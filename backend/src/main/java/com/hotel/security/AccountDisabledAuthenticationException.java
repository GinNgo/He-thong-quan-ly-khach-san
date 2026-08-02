package com.hotel.security;

import org.springframework.security.authentication.DisabledException;

public class AccountDisabledAuthenticationException extends DisabledException {

    public static final String ERROR_CODE = "ACCOUNT_DISABLED";
    public static final String DEFAULT_MESSAGE = "This account is not active.";

    public AccountDisabledAuthenticationException() {
        super(DEFAULT_MESSAGE);
    }

    public static boolean causedByAccountDisabled(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AccountDisabledAuthenticationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

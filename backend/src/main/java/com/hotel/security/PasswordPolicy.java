package com.hotel.security;

/** Shared password length contract for registration, reset and authenticated change flows. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 256;
    public static final String LENGTH_MESSAGE = "Password must be between 8 and 256 characters";

    private PasswordPolicy() {
    }

    public static void requireValid(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(LENGTH_MESSAGE);
        }
    }
}

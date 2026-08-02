package com.hotel.security;

import com.hotel.entities.User;

import java.util.Locale;

public final class AccountStatusPolicy {

    public static final String ACTIVE = "ACTIVE";

    private AccountStatusPolicy() {
    }

    public static User requireActive(User user) {
        if (user == null || !ACTIVE.equals(normalize(user.getStatus()))) {
            throw new AccountDisabledAuthenticationException();
        }
        return user;
    }

    public static boolean isActive(String status) {
        return ACTIVE.equals(normalize(status));
    }

    private static String normalize(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }
}

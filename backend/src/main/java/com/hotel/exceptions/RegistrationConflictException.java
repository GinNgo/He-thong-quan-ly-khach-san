package com.hotel.exceptions;

import java.util.Map;

public class RegistrationConflictException extends RuntimeException {

    public static final String USERNAME_CODE = "USERNAME_ALREADY_EXISTS";
    public static final String EMAIL_CODE = "EMAIL_ALREADY_EXISTS";

    private final String code;
    private final Map<String, String> fieldErrors;

    private RegistrationConflictException(String code, String message, String field, String fieldMessage) {
        super(message);
        this.code = code;
        this.fieldErrors = Map.of(field, fieldMessage);
    }

    public static RegistrationConflictException username() {
        return new RegistrationConflictException(
                USERNAME_CODE,
                "An account with this username already exists.",
                "username",
                "Username is already registered.");
    }

    public static RegistrationConflictException email() {
        return new RegistrationConflictException(
                EMAIL_CODE,
                "An account with this email already exists.",
                "email",
                "Email is already registered.");
    }

    public String code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}

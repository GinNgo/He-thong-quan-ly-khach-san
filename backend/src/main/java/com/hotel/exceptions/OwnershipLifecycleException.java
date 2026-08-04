package com.hotel.exceptions;

public class OwnershipLifecycleException extends IllegalStateException {
    private final String code;
    public OwnershipLifecycleException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}

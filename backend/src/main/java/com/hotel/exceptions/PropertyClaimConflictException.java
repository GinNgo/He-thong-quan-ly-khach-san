package com.hotel.exceptions;

public class PropertyClaimConflictException extends IllegalStateException {
    public static final String GENERIC_CODE = "PROPERTY_CLAIM_CONFLICT";
    public static final String ALREADY_PENDING_CODE = "PROPERTY_CLAIM_ALREADY_PENDING";
    public static final String NOT_PENDING_CODE = "PROPERTY_CLAIM_NOT_PENDING";

    private final String code;
    private final String currentState;

    private PropertyClaimConflictException(String code, String message, String currentState) {
        super(message);
        this.code = code;
        this.currentState = currentState;
    }

    public static PropertyClaimConflictException alreadyPending() {
        return new PropertyClaimConflictException(ALREADY_PENDING_CODE,
                "A pending claim already exists for this account and property.", "PENDING");
    }

    public static PropertyClaimConflictException notPending(String currentState) {
        return new PropertyClaimConflictException(NOT_PENDING_CODE,
                "The property claim is no longer pending.", currentState);
    }

    public static PropertyClaimConflictException concurrentConflict() {
        return new PropertyClaimConflictException(GENERIC_CODE,
                "The property claim changed concurrently. Refresh and try again.", null);
    }

    public String code() { return code; }
    public String currentState() { return currentState; }
}

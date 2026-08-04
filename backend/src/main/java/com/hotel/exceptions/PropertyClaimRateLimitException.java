package com.hotel.exceptions;

public class PropertyClaimRateLimitException extends RuntimeException {

    public static final String ERROR_CODE = "PROPERTY_CLAIM_RATE_LIMITED";
    public static final String DEFAULT_MESSAGE = "Too many property claim requests. Try again later.";

    private final long retryAfterSeconds;

    public PropertyClaimRateLimitException(long retryAfterSeconds) {
        super(DEFAULT_MESSAGE);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

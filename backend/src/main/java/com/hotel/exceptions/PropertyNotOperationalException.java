package com.hotel.exceptions;

import java.util.Locale;

public class PropertyNotOperationalException extends RuntimeException {

    public static final String ERROR_CODE = "PROPERTY_NOT_OPERATIONAL";
    public static final String DEFAULT_MESSAGE =
            "Property operations require approval status APPROVED and operation status ACTIVE.";

    private final String approvalStatus;
    private final String operationStatus;

    public PropertyNotOperationalException(String approvalStatus, String operationStatus) {
        super(DEFAULT_MESSAGE);
        this.approvalStatus = normalize(approvalStatus);
        this.operationStatus = normalize(operationStatus);
    }

    public String approvalStatus() {
        return approvalStatus;
    }

    public String operationStatus() {
        return operationStatus;
    }

    public String currentState() {
        return "approval=" + approvalStatus + ";operation=" + operationStatus;
    }

    private static String normalize(String status) {
        return status == null || status.isBlank()
                ? "UNKNOWN"
                : status.trim().toUpperCase(Locale.ROOT);
    }
}

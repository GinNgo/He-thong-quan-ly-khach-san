package com.hotel.paymentprovider.error;

import java.util.Map;

/** Exception whose public representation is deliberately separate from internal diagnostics. */
public class FinancialException extends RuntimeException {

    private final FinancialErrorCode code;
    private final Map<String, String> fieldErrors;
    private final String currentState;

    public FinancialException(FinancialErrorCode code) {
        this(code, code.defaultMessage(), null, null, null);
    }

    public FinancialException(FinancialErrorCode code, String safeMessage) {
        this(code, safeMessage, null, null, null);
    }

    public FinancialException(FinancialErrorCode code, String safeMessage,
                              Map<String, String> fieldErrors, String currentState, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
        this.currentState = currentState;
    }

    public FinancialErrorCode code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    public String currentState() {
        return currentState;
    }
}

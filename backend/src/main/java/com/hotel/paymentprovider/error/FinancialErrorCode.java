package com.hotel.paymentprovider.error;

import org.springframework.http.HttpStatus;

/** Stable, client-safe error identifiers for financial workflows. */
public enum FinancialErrorCode {
    TENANT_ACCESS_DENIED(HttpStatus.FORBIDDEN, false, "Access to this property financial resource is denied."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, false, "The requested financial resource was not found."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, false, "The amount is invalid."),
    INVALID_CURRENCY(HttpStatus.BAD_REQUEST, false, "Only VND is supported."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, false, "The financial state transition is not allowed."),
    OUTSTANDING_BALANCE(HttpStatus.CONFLICT, false, "The outstanding balance must be settled before checkout."),
    OVERPAYMENT_REQUIRES_RESOLUTION(HttpStatus.CONFLICT, false, "The overpayment requires an explicit resolution."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, false, "The idempotency key was reused with a different request."),
    CALLBACK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, false, "The provider callback signature is invalid."),
    CALLBACK_MERCHANT_MISMATCH(HttpStatus.BAD_REQUEST, false, "The provider merchant does not match the expected merchant."),
    CALLBACK_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, false, "The provider amount does not match the expected amount."),
    CALLBACK_REFERENCE_MISMATCH(HttpStatus.BAD_REQUEST, false, "The provider reference does not match the expected payment."),
    ATTEMPT_EXPIRED(HttpStatus.CONFLICT, false, "The payment attempt has expired."),
    REFUND_EXCEEDS_BALANCE(HttpStatus.CONFLICT, false, "The refund exceeds the refundable balance."),
    POLICY_NOT_CONFIGURED(HttpStatus.CONFLICT, false, "The requested financial policy is not configured."),
    PAYMENT_ENVIRONMENT_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, true, "The payment environment is disabled."),
    PRODUCTION_NOT_APPROVED(HttpStatus.SERVICE_UNAVAILABLE, false, "Production payment is not approved."),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, true, "The payment provider is temporarily unavailable."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, true, "The financial resource changed concurrently; retry safely."),
    EXPORT_RECONCILIATION_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, false, "The export failed reconciliation checks.");

    private final HttpStatus status;
    private final boolean retryable;
    private final String defaultMessage;

    FinancialErrorCode(HttpStatus status, boolean retryable, String defaultMessage) {
        this.status = status;
        this.retryable = retryable;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

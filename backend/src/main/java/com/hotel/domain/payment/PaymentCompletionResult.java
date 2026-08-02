package com.hotel.domain.payment;

public enum PaymentCompletionResult {
    APPLIED,
    IDEMPOTENT,
    RECONCILIATION_REQUIRED
}

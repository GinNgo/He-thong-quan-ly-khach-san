package com.hotel.domain.lifecycle;

public enum TransitionDecision {
    APPLY,
    IDEMPOTENT,
    REJECT,
    RECONCILIATION_REQUIRED
}

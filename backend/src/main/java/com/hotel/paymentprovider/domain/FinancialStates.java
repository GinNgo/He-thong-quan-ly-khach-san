package com.hotel.paymentprovider.domain;

public final class FinancialStates {

    private FinancialStates() {
    }

    public enum PaymentState {
        CREATED,
        PENDING,
        PENDING_VERIFICATION,
        PROCESSING,
        SUCCESS,
        FAILED,
        CANCELLED,
        PARTIALLY_REFUNDED,
        REFUNDED,
        EXPIRED
    }

    public enum BookingFinancialState {
        UNPAID,
        PARTIALLY_PAID,
        DEPOSIT_PAID,
        PAID,
        OVERPAID,
        PARTIALLY_REFUNDED,
        REFUNDED
    }

    public enum RefundState {
        REQUESTED,
        PENDING_APPROVAL,
        PENDING_PROVIDER,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public enum SubscriptionOrderState {
        CREATED,
        PENDING_PAYMENT,
        PAID,
        APPLIED,
        FAILED,
        CANCELLED,
        EXPIRED,
        REFUNDED
    }
}

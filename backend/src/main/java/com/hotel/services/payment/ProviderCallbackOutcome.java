package com.hotel.services.payment;

public enum ProviderCallbackOutcome {
    CONFIRMED,
    DUPLICATE,
    FAILED_RECORDED,
    NOT_FOUND,
    INVALID_AMOUNT
}

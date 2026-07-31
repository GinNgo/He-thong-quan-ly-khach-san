package com.hotel.paymentprovider.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact non-negative VND amount used at financial boundaries. */
public record VndMoney(BigDecimal amount) {

    public VndMoney {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.scale() > 0) {
            amount = amount.setScale(0, RoundingMode.UNNECESSARY);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("VND amount must not be negative.");
        }
        amount = amount.setScale(0, RoundingMode.UNNECESSARY);
    }

    public static VndMoney of(BigDecimal amount) {
        return new VndMoney(amount);
    }

    public static VndMoney of(long amount) {
        return new VndMoney(BigDecimal.valueOf(amount));
    }

    public static VndMoney zero() {
        return of(0);
    }

    public VndMoney add(VndMoney other) {
        Objects.requireNonNull(other, "other must not be null");
        return new VndMoney(amount.add(other.amount));
    }

    public VndMoney subtract(VndMoney other) {
        Objects.requireNonNull(other, "other must not be null");
        return new VndMoney(amount.subtract(other.amount));
    }
}

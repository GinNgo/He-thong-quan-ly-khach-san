package com.hotel.services.payment;

import com.hotel.domain.payment.PaymentProvider;

import java.math.BigDecimal;

public record ProviderCallbackData(
        PaymentProvider provider,
        String providerReference,
        String providerTransactionId,
        BigDecimal amount,
        boolean successful,
        String failureCode) {
}

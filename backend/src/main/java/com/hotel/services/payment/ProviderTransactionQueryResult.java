package com.hotel.services.payment;

import com.hotel.domain.payment.PaymentProvider;

import java.math.BigDecimal;

public record ProviderTransactionQueryResult(
        PaymentProvider provider,
        ProviderOperationStatus status,
        String providerReference,
        String providerTransactionId,
        BigDecimal amount,
        String responseCode,
        String message) {
}

package com.hotel.services.payment;

import com.hotel.domain.payment.PaymentProvider;

import java.math.BigDecimal;

public record ProviderRefundResult(
        PaymentProvider provider,
        ProviderOperationStatus status,
        String providerRefundReference,
        String providerTransactionId,
        BigDecimal amount,
        String responseCode,
        String message) {
}

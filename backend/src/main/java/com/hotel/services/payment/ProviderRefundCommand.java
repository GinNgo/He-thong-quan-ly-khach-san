package com.hotel.services.payment;

import java.math.BigDecimal;

public record ProviderRefundCommand(
        String providerRefundReference,
        String requestId,
        String originalProviderTransactionId,
        BigDecimal amount,
        String description) {
}

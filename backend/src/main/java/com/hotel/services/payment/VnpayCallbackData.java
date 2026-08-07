package com.hotel.services.payment;

import java.math.BigDecimal;

public record VnpayCallbackData(
        String providerReference,
        String providerTransactionId,
        BigDecimal amount,
        String responseCode,
        String transactionStatus,
        boolean successful) {
}

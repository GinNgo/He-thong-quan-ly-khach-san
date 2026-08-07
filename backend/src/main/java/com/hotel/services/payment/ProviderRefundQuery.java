package com.hotel.services.payment;

import java.math.BigDecimal;

public record ProviderRefundQuery(
        String providerRefundReference,
        String requestId,
        BigDecimal expectedAmount) {
}

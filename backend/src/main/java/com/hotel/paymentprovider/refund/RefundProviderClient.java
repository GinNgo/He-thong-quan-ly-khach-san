package com.hotel.paymentprovider.refund;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

public interface RefundProviderClient {

    String provider();

    PreparedRefund prepare(PrepareRefund request);

    record PrepareRefund(
            PaymentEnvironment environment,
            String refundPublicId,
            BigDecimal amount,
            String currency,
            String reference,
            String merchantId,
            Map<String, ?> credentials,
            URI endpoint) {
    }

    record PreparedRefund(String providerReference, boolean dispatched, String externalStatus) {
    }
}

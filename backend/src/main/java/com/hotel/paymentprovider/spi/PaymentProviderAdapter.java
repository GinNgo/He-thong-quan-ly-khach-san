package com.hotel.paymentprovider.spi;

import com.hotel.paymentprovider.error.FinancialErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Shared provider boundary. Adapters verify data; context services apply domain effects. */
public interface PaymentProviderAdapter {

    String provider();

    VerificationResult verify(VerificationRequest request);

    NormalizedCallback normalize(VerificationRequest request);

    default RetryClassification classifyFailure(Throwable failure) {
        return RetryClassification.RETRYABLE;
    }

    record VerificationRequest(String merchantId, String expectedMerchantId, BigDecimal expectedAmount,
                               BigDecimal callbackAmount, String expectedCurrency, String callbackCurrency,
                               String expectedReference, String callbackReference, String signature,
                               Map<String, ?> payload) {
    }

    record NormalizedCallback(String provider, String eventId, String transactionId, String reference,
                              BigDecimal amount, String currency, Instant occurredAt, Map<String, ?> metadata) {
    }

    record VerificationResult(boolean accepted, FinancialErrorCode errorCode, boolean retryable) {
        public static VerificationResult acceptedResult() {
            return new VerificationResult(true, null, false);
        }

        public static VerificationResult rejectedResult(FinancialErrorCode errorCode) {
            return new VerificationResult(false, errorCode, errorCode.retryable());
        }
    }

    enum RetryClassification {
        RETRYABLE,
        NON_RETRYABLE,
        UNKNOWN
    }
}

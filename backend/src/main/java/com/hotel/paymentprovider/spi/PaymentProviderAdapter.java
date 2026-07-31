package com.hotel.paymentprovider.spi;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared provider boundary. Adapters verify data; context services apply domain effects. */
public interface PaymentProviderAdapter {

    String provider();

    VerificationResult verify(VerificationRequest request);

    NormalizedCallback normalize(VerificationRequest request);

    default RetryClassification classifyFailure(Throwable failure) {
        if (failure instanceof FinancialException financialException) {
            return financialException.code().retryable()
                    ? RetryClassification.RETRYABLE
                    : RetryClassification.NON_RETRYABLE;
        }
        if (failure instanceof java.net.ConnectException
                || failure instanceof java.net.SocketTimeoutException
                || failure instanceof java.net.http.HttpTimeoutException) {
            return RetryClassification.RETRYABLE;
        }
        if (failure instanceof IllegalArgumentException) {
            return RetryClassification.NON_RETRYABLE;
        }
        return RetryClassification.UNKNOWN;
    }

    record VerificationRequest(String merchantId, String expectedMerchantId, BigDecimal expectedAmount,
                               BigDecimal callbackAmount, String expectedCurrency, String callbackCurrency,
                               String expectedReference, String callbackReference, String signature,
                               Map<String, ?> payload, Map<String, ?> credentials,
                               Instant attemptExpiresAt, Instant receivedAt) {

        public VerificationRequest(String merchantId, String expectedMerchantId, BigDecimal expectedAmount,
                                   BigDecimal callbackAmount, String expectedCurrency, String callbackCurrency,
                                   String expectedReference, String callbackReference, String signature,
                                   Map<String, ?> payload) {
            this(merchantId, expectedMerchantId, expectedAmount, callbackAmount, expectedCurrency,
                    callbackCurrency, expectedReference, callbackReference, signature, payload,
                    Map.of(), null, null);
        }

        public VerificationRequest(String merchantId, String expectedMerchantId, BigDecimal expectedAmount,
                                   BigDecimal callbackAmount, String expectedCurrency, String callbackCurrency,
                                   String expectedReference, String callbackReference, String signature,
                                   Map<String, ?> payload, Map<String, ?> credentials) {
            this(merchantId, expectedMerchantId, expectedAmount, callbackAmount, expectedCurrency,
                    callbackCurrency, expectedReference, callbackReference, signature, payload,
                    credentials, null, null);
        }

        public VerificationRequest {
            payload = immutableMap(payload);
            credentials = immutableMap(credentials);
        }

        public boolean expired() {
            return attemptExpiresAt != null && receivedAt != null && !receivedAt.isBefore(attemptExpiresAt);
        }

        @Override
        public String toString() {
            return "VerificationRequest[merchantId=" + merchantId
                    + ", expectedMerchantId=" + expectedMerchantId
                    + ", expectedAmount=" + expectedAmount
                    + ", callbackAmount=" + callbackAmount
                    + ", expectedCurrency=" + expectedCurrency
                    + ", callbackCurrency=" + callbackCurrency
                    + ", expectedReference=" + expectedReference
                    + ", callbackReference=" + callbackReference
                    + ", signature=<redacted>, payloadKeys=" + payload.keySet()
                    + ", credentials=<redacted>, attemptExpiresAt=" + attemptExpiresAt
                    + ", receivedAt=" + receivedAt + ']';
        }

        private static Map<String, ?> immutableMap(Map<String, ?> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
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

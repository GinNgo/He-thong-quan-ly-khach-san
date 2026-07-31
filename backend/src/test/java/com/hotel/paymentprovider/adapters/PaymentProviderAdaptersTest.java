package com.hotel.paymentprovider.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentProviderAdaptersTest {

    @Test
    void vnpayVerifiesProviderSignatureAndNormalizesServerBindings() {
        VnpayPaymentProviderAdapter adapter = new VnpayPaymentProviderAdapter();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vnp_TmnCode", "HOTEL_001");
        payload.put("vnp_Amount", "35000000");
        payload.put("vnp_CurrCode", "VND");
        payload.put("vnp_TxnRef", "PAY-REF-1");
        payload.put("vnp_TransactionNo", "14927984");
        payload.put("vnp_ResponseCode", "00");
        payload.put("vnp_TransactionStatus", "00");
        payload.put("vnp_PayDate", "20260731181500");
        String secret = "vnpay-sandbox-secret";
        String signature = ProviderAdapterSupport.hmacHex(
                "HmacSHA512", secret,
                ProviderAdapterSupport.canonicalQuery(
                        payload, "vnp_", List.of("vnp_SecureHash", "vnp_SecureHashType"),
                        StandardCharsets.US_ASCII));
        payload.put("vnp_SecureHash", signature);

        PaymentProviderAdapter.VerificationRequest request = request(
                "HOTEL_001", BigDecimal.valueOf(350_000), "PAY-REF-1", signature,
                payload, Map.of("hashSecret", secret));

        assertTrue(adapter.verify(request).accepted());
        PaymentProviderAdapter.NormalizedCallback callback = adapter.normalize(request);
        assertEquals("VNPAY", callback.provider());
        assertEquals("14927984", callback.eventId());
        assertEquals(BigDecimal.valueOf(350_000), callback.amount());
        assertEquals(Boolean.TRUE, callback.metadata().get("successful"));

        Map<String, Object> wrongCurrencyPayload = new LinkedHashMap<>(payload);
        wrongCurrencyPayload.put("vnp_CurrCode", "USD");
        String wrongCurrencySignature = ProviderAdapterSupport.hmacHex(
                "HmacSHA512", secret,
                ProviderAdapterSupport.canonicalQuery(
                        wrongCurrencyPayload, "vnp_", List.of("vnp_SecureHash", "vnp_SecureHashType"),
                        StandardCharsets.US_ASCII));
        wrongCurrencyPayload.put("vnp_SecureHash", wrongCurrencySignature);
        PaymentProviderAdapter.VerificationRequest wrongCurrency = request(
                "HOTEL_001", BigDecimal.valueOf(350_000), "PAY-REF-1", wrongCurrencySignature,
                wrongCurrencyPayload, Map.of("hashSecret", secret));
        assertEquals(FinancialErrorCode.INVALID_CURRENCY, adapter.verify(wrongCurrency).errorCode());
    }

    @Test
    void momoVerifiesOfficialCallbackPayloadAndRejectsAmountTampering() {
        MomoPaymentProviderAdapter adapter = new MomoPaymentProviderAdapter();
        Map<String, Object> payload = momoPayload();
        String accessKey = "momo-access";
        String secretKey = "momo-secret";
        String signature = ProviderAdapterSupport.hmacHex(
                "HmacSHA256", secretKey, adapter.signaturePayload(payload, accessKey));
        payload.put("signature", signature);
        PaymentProviderAdapter.VerificationRequest accepted = request(
                "MOMO_TEST", BigDecimal.valueOf(350_000), "MOMO-ORDER-1", signature,
                payload, Map.of("accessKey", accessKey, "secretKey", secretKey));

        assertTrue(adapter.verify(accepted).accepted());
        assertEquals("230407000006575", adapter.normalize(accepted).transactionId());

        PaymentProviderAdapter.VerificationRequest tamperedExpectation = request(
                "MOMO_TEST", BigDecimal.valueOf(350_001), "MOMO-ORDER-1", signature,
                payload, Map.of("accessKey", accessKey, "secretKey", secretKey));
        assertEquals(FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH,
                adapter.verify(tamperedExpectation).errorCode());
    }

    @Test
    void zalopayVerifiesRawDataMacAndExpectedAppIdentity() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ZaloPayPaymentProviderAdapter adapter = new ZaloPayPaymentProviderAdapter(objectMapper);
        String data = objectMapper.writeValueAsString(Map.of(
                "app_id", 2554,
                "app_trans_id", "260731_PAY_REF_1",
                "zp_trans_id", 230407000006575L,
                "amount", 350000,
                "server_time", 1785496500000L));
        String key2 = "zalopay-key-2";
        String mac = ProviderAdapterSupport.hmacHex("HmacSHA256", key2, data);
        Map<String, Object> payload = Map.of("type", 1, "data", data, "mac", mac);
        PaymentProviderAdapter.VerificationRequest request = request(
                "2554", BigDecimal.valueOf(350_000), "260731_PAY_REF_1", mac,
                payload, Map.of("key2", key2));

        assertTrue(adapter.verify(request).accepted());
        assertEquals("230407000006575", adapter.normalize(request).eventId());

        PaymentProviderAdapter.VerificationRequest wrongMerchant = request(
                "2555", BigDecimal.valueOf(350_000), "260731_PAY_REF_1", mac,
                payload, Map.of("key2", key2));
        assertEquals(FinancialErrorCode.CALLBACK_MERCHANT_MISMATCH,
                adapter.verify(wrongMerchant).errorCode());
    }

    @Test
    void simulatorUsesSignedDeterministicPayloadAndEnforcesExpiry() {
        SimulatorPaymentProviderAdapter adapter = new SimulatorPaymentProviderAdapter();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", "SIM-HOTEL-1");
        payload.put("eventId", "SIM-EVENT-1");
        payload.put("transactionId", "SIM-TXN-1");
        payload.put("reference", "PAY-REF-1");
        payload.put("amount", 350000);
        payload.put("currency", "VND");
        payload.put("occurredAt", "2026-07-31T11:15:00Z");
        payload.put("status", "SUCCEEDED");
        String secret = "simulator-signing-secret-with-32-chars";
        String signature = ProviderAdapterSupport.hmacHex(
                "HmacSHA256", secret,
                ProviderAdapterSupport.canonicalQuery(payload, null, List.of("signature")));
        payload.put("signature", signature);

        PaymentProviderAdapter.VerificationRequest accepted = request(
                "SIM-HOTEL-1", BigDecimal.valueOf(350_000), "PAY-REF-1", signature,
                payload, Map.of("signingSecret", secret));
        assertTrue(adapter.verify(accepted).accepted());
        assertEquals(Boolean.TRUE, adapter.normalize(accepted).metadata().get("successful"));

        PaymentProviderAdapter.VerificationRequest expired = new PaymentProviderAdapter.VerificationRequest(
                "SIM-HOTEL-1", "SIM-HOTEL-1", BigDecimal.valueOf(350_000),
                BigDecimal.valueOf(350_000), "VND", "VND", "PAY-REF-1", "PAY-REF-1",
                signature, payload, Map.of("signingSecret", secret),
                Instant.parse("2026-07-31T11:14:59Z"), Instant.parse("2026-07-31T11:15:00Z"));
        assertEquals(FinancialErrorCode.ATTEMPT_EXPIRED, adapter.verify(expired).errorCode());
    }

    @Test
    void invalidSignaturesAndMissingCredentialsFailClosed() {
        VnpayPaymentProviderAdapter adapter = new VnpayPaymentProviderAdapter();
        Map<String, Object> payload = Map.of(
                "vnp_TmnCode", "HOTEL_001",
                "vnp_Amount", "35000000",
                "vnp_CurrCode", "VND",
                "vnp_TxnRef", "PAY-REF-1");

        PaymentProviderAdapter.VerificationResult unavailable = adapter.verify(request(
                "HOTEL_001", BigDecimal.valueOf(350_000), "PAY-REF-1", "bad", payload, Map.of()));
        assertEquals(FinancialErrorCode.PROVIDER_UNAVAILABLE, unavailable.errorCode());
        assertTrue(unavailable.retryable());

        PaymentProviderAdapter.VerificationResult invalid = adapter.verify(request(
                "HOTEL_001", BigDecimal.valueOf(350_000), "PAY-REF-1", "bad", payload,
                Map.of("hashSecret", "configured-secret")));
        assertEquals(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID, invalid.errorCode());
        assertFalse(invalid.retryable());
    }

    @Test
    void registryResolvesProvidersCaseInsensitivelyAndRejectsUnknownValues() {
        PaymentProviderAdapterRegistry registry = new PaymentProviderAdapterRegistry(List.of(
                new VnpayPaymentProviderAdapter(),
                new MomoPaymentProviderAdapter(),
                new ZaloPayPaymentProviderAdapter(new ObjectMapper()),
                new SimulatorPaymentProviderAdapter()));

        assertTrue(registry.supports("momo"));
        assertFalse(registry.supports(null));
        assertEquals("ZALOPAY", registry.require(" zalopay ").provider());
        FinancialException exception = assertThrows(FinancialException.class,
                () -> registry.require("unknown"));
        assertEquals(FinancialErrorCode.PROVIDER_UNAVAILABLE, exception.code());
        assertEquals(PaymentProviderAdapter.RetryClassification.NON_RETRYABLE,
                registry.require("momo").classifyFailure(new IllegalArgumentException("invalid payload")));
        assertEquals(PaymentProviderAdapter.RetryClassification.UNKNOWN,
                registry.require("momo").classifyFailure(new IllegalStateException("unexpected")));
        assertFalse(request("merchant", BigDecimal.ONE, "reference", "secret-signature",
                Map.of("signature", "payload-signature"), Map.of("secretKey", "credential-secret"))
                .toString().contains("credential-secret"));
    }

    private PaymentProviderAdapter.VerificationRequest request(
            String merchant,
            BigDecimal amount,
            String reference,
            String signature,
            Map<String, ?> payload,
            Map<String, ?> credentials) {
        return new PaymentProviderAdapter.VerificationRequest(
                merchant, merchant, amount, amount, "VND", "VND", reference, reference,
                signature, payload, credentials);
    }

    private Map<String, Object> momoPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", "MOMO_TEST");
        payload.put("orderId", "MOMO-ORDER-1");
        payload.put("requestId", "request-1");
        payload.put("amount", 350000);
        payload.put("orderInfo", "Thanh toan dat phong 42");
        payload.put("orderType", "momo_wallet");
        payload.put("transId", 230407000006575L);
        payload.put("resultCode", 0);
        payload.put("message", "Successful.");
        payload.put("payType", "qr");
        payload.put("responseTime", 1785496500000L);
        payload.put("extraData", "");
        return payload;
    }
}

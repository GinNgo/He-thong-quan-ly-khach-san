package com.hotel.paymentprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.adapters.MomoPaymentProviderAdapter;
import com.hotel.paymentprovider.adapters.SimulatorPaymentProviderAdapter;
import com.hotel.paymentprovider.adapters.VnpayPaymentProviderAdapter;
import com.hotel.paymentprovider.adapters.ZaloPayPaymentProviderAdapter;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyProviderContractTest {

    private static final BigDecimal AMOUNT = BigDecimal.valueOf(350_000);
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-31T14:00:00Z");

    @ParameterizedTest(name = "{0} accepts a correctly signed and bound callback")
    @MethodSource("providers")
    void acceptsSignedCallbacksAndNormalizesRequiredEvidence(String provider, Fixture fixture) {
        PaymentProviderAdapter.VerificationRequest request = fixture.request(
                fixture.merchant(), AMOUNT, "VND", fixture.reference());

        PaymentProviderAdapter.VerificationResult verification = fixture.adapter().verify(request);
        PaymentProviderAdapter.NormalizedCallback normalized = fixture.adapter().normalize(request);

        assertTrue(verification.accepted());
        assertEquals(provider, normalized.provider());
        assertEquals(0, AMOUNT.compareTo(normalized.amount()));
        assertEquals("VND", normalized.currency());
        assertEquals(fixture.reference(), normalized.reference());
        assertFalse(normalized.eventId() == null || normalized.eventId().isBlank());
        assertNotNull(normalized.metadata());
    }

    @ParameterizedTest(name = "{0} rejects invalid signatures and missing server credentials")
    @MethodSource("providers")
    void rejectsInvalidSignatureAndMissingCredentials(String provider, Fixture fixture) {
        PaymentProviderAdapter.VerificationRequest invalidSignature = fixture.securityRequest(
                fixture.merchant(), AMOUNT, "VND", fixture.reference(), "invalid-signature",
                fixture.credentials());
        assertRejected(
                fixture.adapter().verify(invalidSignature),
                FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);

        PaymentProviderAdapter.VerificationRequest missingCredentials = fixture.securityRequest(
                fixture.merchant(), AMOUNT, "VND", fixture.reference(), fixture.signature(), Map.of());
        PaymentProviderAdapter.VerificationResult result = fixture.adapter().verify(missingCredentials);
        assertRejected(result, FinancialErrorCode.PROVIDER_UNAVAILABLE);
        assertTrue(result.retryable());
    }

    @ParameterizedTest(name = "{0} enforces merchant, amount, currency and reference bindings")
    @MethodSource("providers")
    void enforcesAllServerOwnedBindings(String provider, Fixture fixture) {
        assertRejected(
                fixture.adapter().verify(fixture.request(
                        fixture.merchant() + "-OTHER", AMOUNT, "VND", fixture.reference())),
                FinancialErrorCode.CALLBACK_MERCHANT_MISMATCH);
        assertRejected(
                fixture.adapter().verify(fixture.request(
                        fixture.merchant(), AMOUNT.add(BigDecimal.ONE), "VND", fixture.reference())),
                FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH);
        assertRejected(
                fixture.adapter().verify(fixture.request(
                        fixture.merchant(), AMOUNT, "USD", fixture.reference())),
                FinancialErrorCode.INVALID_CURRENCY);
        assertRejected(
                fixture.adapter().verify(fixture.request(
                        fixture.merchant(), AMOUNT, "VND", fixture.reference() + "-OTHER")),
                FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);

        assertRejected(
                fixture.adapter().verify(fixture.callbackBindingRequest(
                        fixture.merchant(), AMOUNT, "VND", fixture.reference(),
                        AMOUNT.add(BigDecimal.ONE), "VND", fixture.reference())),
                FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH);
        assertRejected(
                fixture.adapter().verify(fixture.callbackBindingRequest(
                        fixture.merchant(), AMOUNT, "VND", fixture.reference(),
                        AMOUNT, "USD", fixture.reference())),
                FinancialErrorCode.INVALID_CURRENCY);
        assertRejected(
                fixture.adapter().verify(fixture.callbackBindingRequest(
                        fixture.merchant(), AMOUNT, "VND", fixture.reference(),
                        AMOUNT, "VND", "OTHER-REFERENCE")),
                FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
    }

    @ParameterizedTest(name = "{0} rejects callbacks at expiry and accepts the instant before")
    @MethodSource("providers")
    void enforcesAttemptExpiryBoundary(String provider, Fixture fixture) {
        PaymentProviderAdapter.VerificationRequest expired = fixture.expiryRequest(
                fixture.merchant(), AMOUNT, "VND", fixture.reference(),
                EXPIRES_AT, EXPIRES_AT);
        assertRejected(fixture.adapter().verify(expired), FinancialErrorCode.ATTEMPT_EXPIRED);

        PaymentProviderAdapter.VerificationRequest justBeforeExpiry = fixture.expiryRequest(
                fixture.merchant(), AMOUNT, "VND", fixture.reference(),
                EXPIRES_AT, EXPIRES_AT.minusNanos(1));
        assertTrue(fixture.adapter().verify(justBeforeExpiry).accepted());
    }

    private static Stream<Arguments> providers() throws Exception {
        return Stream.of(
                Arguments.of("VNPAY", vnpay()),
                Arguments.of("MOMO", momo()),
                Arguments.of("ZALOPAY", zalopay()),
                Arguments.of("SIMULATOR", simulator()));
    }

    private static Fixture vnpay() {
        String merchant = "HOTEL_001";
        String reference = "PAY-REF-1";
        String secret = "vnpay-sandbox-secret";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vnp_TmnCode", merchant);
        payload.put("vnp_Amount", "35000000");
        payload.put("vnp_CurrCode", "VND");
        payload.put("vnp_TxnRef", reference);
        payload.put("vnp_TransactionNo", "14927984");
        payload.put("vnp_ResponseCode", "00");
        payload.put("vnp_TransactionStatus", "00");
        payload.put("vnp_PayDate", "20260731205959");
        String signature = hmac(
                "HmacSHA512",
                secret,
                canonical(payload, "vnp_", List.of("vnp_SecureHash", "vnp_SecureHashType"),
                        StandardCharsets.US_ASCII));
        payload.put("vnp_SecureHash", signature);
        return new Fixture(
                new VnpayPaymentProviderAdapter(), merchant, reference, signature,
                Map.copyOf(payload), Map.of("hashSecret", secret));
    }

    private static Fixture momo() {
        String merchant = "MOMO_TEST";
        String reference = "MOMO-ORDER-1";
        String accessKey = "momo-access";
        String secretKey = "momo-secret";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", merchant);
        payload.put("orderId", reference);
        payload.put("requestId", "request-1");
        payload.put("amount", 350000);
        payload.put("orderInfo", "Thanh toan dat phong 42");
        payload.put("orderType", "momo_wallet");
        payload.put("transId", 230407000006575L);
        payload.put("resultCode", 0);
        payload.put("message", "Successful.");
        payload.put("payType", "qr");
        payload.put("responseTime", 1785502799000L);
        payload.put("extraData", "");
        String signature = hmac("HmacSHA256", secretKey, momoSignaturePayload(payload, accessKey));
        payload.put("signature", signature);
        return new Fixture(
                new MomoPaymentProviderAdapter(), merchant, reference, signature,
                Map.copyOf(payload), Map.of("accessKey", accessKey, "secretKey", secretKey));
    }

    private static Fixture zalopay() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String merchant = "2554";
        String reference = "260731_PAY_REF_1";
        String key2 = "zalopay-key-2";
        String data = objectMapper.writeValueAsString(Map.of(
                "app_id", 2554,
                "app_trans_id", reference,
                "zp_trans_id", 230407000006575L,
                "amount", 350000,
                "server_time", 1785502799000L));
        String signature = hmac("HmacSHA256", key2, data);
        Map<String, Object> payload = Map.of("type", 1, "data", data, "mac", signature);
        return new Fixture(
                new ZaloPayPaymentProviderAdapter(objectMapper), merchant, reference, signature,
                payload, Map.of("key2", key2));
    }

    private static Fixture simulator() {
        String merchant = "PROPERTY-SIMULATOR";
        String reference = "PAY-REF-1";
        String secret = "simulator-signing-secret-with-32-chars";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", merchant);
        payload.put("eventId", "SIM-EVENT-1");
        payload.put("transactionId", "SIM-TXN-1");
        payload.put("reference", reference);
        payload.put("amount", 350000);
        payload.put("currency", "VND");
        payload.put("occurredAt", "2026-07-31T13:30:00Z");
        payload.put("status", "SUCCEEDED");
        String signature = hmac(
                "HmacSHA256", secret,
                canonical(payload, null, List.of("signature"), StandardCharsets.UTF_8));
        payload.put("signature", signature);
        return new Fixture(
                new SimulatorPaymentProviderAdapter(), merchant, reference, signature,
                Map.copyOf(payload), Map.of("signingSecret", secret));
    }

    private static String momoSignaturePayload(Map<String, ?> payload, String accessKey) {
        return "accessKey=" + accessKey
                + "&amount=" + raw(payload.get("amount"))
                + "&extraData=" + raw(payload.get("extraData"))
                + "&message=" + raw(payload.get("message"))
                + "&orderId=" + raw(payload.get("orderId"))
                + "&orderInfo=" + raw(payload.get("orderInfo"))
                + "&orderType=" + raw(payload.get("orderType"))
                + "&partnerCode=" + raw(payload.get("partnerCode"))
                + "&payType=" + raw(payload.get("payType"))
                + "&requestId=" + raw(payload.get("requestId"))
                + "&responseTime=" + raw(payload.get("responseTime"))
                + "&resultCode=" + raw(payload.get("resultCode"))
                + "&transId=" + raw(payload.get("transId"));
    }

    private static String canonical(
            Map<String, ?> payload,
            String prefix,
            List<String> excluded,
            Charset charset) {
        List<String> names = new ArrayList<>(payload.keySet());
        names.removeIf(name -> name == null
                || prefix != null && !name.startsWith(prefix)
                || excluded.contains(name)
                || raw(payload.get(name)).isBlank());
        names.sort(String::compareTo);
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (!result.isEmpty()) {
                result.append('&');
            }
            result.append(URLEncoder.encode(name, charset));
            result.append('=');
            result.append(URLEncoder.encode(raw(payload.get(name)), charset));
        }
        return result.toString();
    }

    private static String hmac(String algorithm, String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign provider fixture.", exception);
        }
    }

    private static String raw(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void assertRejected(
            PaymentProviderAdapter.VerificationResult result,
            FinancialErrorCode expected) {
        assertFalse(result.accepted());
        assertEquals(expected, result.errorCode());
    }

    private record Fixture(
            PaymentProviderAdapter adapter,
            String merchant,
            String reference,
            String signature,
            Map<String, ?> payload,
            Map<String, ?> credentials) {

        PaymentProviderAdapter.VerificationRequest request(
                String expectedMerchant,
                BigDecimal expectedAmount,
                String expectedCurrency,
                String expectedReference) {
            return securityRequest(
                    expectedMerchant, expectedAmount, expectedCurrency, expectedReference,
                    signature, credentials);
        }

        PaymentProviderAdapter.VerificationRequest callbackBindingRequest(
                String expectedMerchant,
                BigDecimal expectedAmount,
                String expectedCurrency,
                String expectedReference,
                BigDecimal callbackAmount,
                String callbackCurrency,
                String callbackReference) {
            return new PaymentProviderAdapter.VerificationRequest(
                    merchant,
                    expectedMerchant,
                    expectedAmount,
                    callbackAmount,
                    expectedCurrency,
                    callbackCurrency,
                    expectedReference,
                    callbackReference,
                    signature,
                    payload,
                    credentials,
                    null,
                    null);
        }

        PaymentProviderAdapter.VerificationRequest expiryRequest(
                String expectedMerchant,
                BigDecimal expectedAmount,
                String expectedCurrency,
                String expectedReference,
                Instant attemptExpiresAt,
                Instant receivedAt) {
            return new PaymentProviderAdapter.VerificationRequest(
                    merchant,
                    expectedMerchant,
                    expectedAmount,
                    AMOUNT,
                    expectedCurrency,
                    "VND",
                    expectedReference,
                    reference,
                    signature,
                    payload,
                    credentials,
                    attemptExpiresAt,
                    receivedAt);
        }

        PaymentProviderAdapter.VerificationRequest securityRequest(
                String expectedMerchant,
                BigDecimal expectedAmount,
                String expectedCurrency,
                String expectedReference,
                String suppliedSignature,
                Map<String, ?> suppliedCredentials) {
            return new PaymentProviderAdapter.VerificationRequest(
                    merchant,
                    expectedMerchant,
                    expectedAmount,
                    AMOUNT,
                    expectedCurrency,
                    "VND",
                    expectedReference,
                    reference,
                    suppliedSignature,
                    payload,
                    suppliedCredentials,
                    null,
                    null);
        }
    }
}

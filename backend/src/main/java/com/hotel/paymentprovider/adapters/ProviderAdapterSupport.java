package com.hotel.paymentprovider.adapters;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter.VerificationRequest;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter.VerificationResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ProviderAdapterSupport {

    private ProviderAdapterSupport() {
    }

    static VerificationResult validateBindings(
            VerificationRequest request,
            String actualMerchant,
            BigDecimal actualAmount,
            String actualCurrency,
            String actualReference) {
        if (!hasText(request.expectedMerchantId())) {
            return VerificationResult.rejectedResult(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (!equalsText(actualMerchant, request.expectedMerchantId())
                || hasText(request.merchantId()) && !equalsText(actualMerchant, request.merchantId())) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_MERCHANT_MISMATCH);
        }
        if (request.expectedAmount() == null || actualAmount == null
                || actualAmount.compareTo(request.expectedAmount()) != 0
                || request.callbackAmount() != null && actualAmount.compareTo(request.callbackAmount()) != 0) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH);
        }
        if (!hasText(request.expectedCurrency()) || !equalsText(actualCurrency, request.expectedCurrency())
                || hasText(request.callbackCurrency()) && !equalsText(actualCurrency, request.callbackCurrency())) {
            return VerificationResult.rejectedResult(FinancialErrorCode.INVALID_CURRENCY);
        }
        if (!hasText(request.expectedReference()) || !equalsText(actualReference, request.expectedReference())
                || hasText(request.callbackReference()) && !equalsText(actualReference, request.callbackReference())) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        if (request.expired()) {
            return VerificationResult.rejectedResult(FinancialErrorCode.ATTEMPT_EXPIRED);
        }
        return VerificationResult.acceptedResult();
    }

    static String credential(VerificationRequest request, String name) {
        return text(request.credentials().get(name));
    }

    static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    static String rawText(Object value) {
        return value == null ? "" : value.toString();
    }

    static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static Integer intValue(Object value) {
        Long parsed = longValue(value);
        return parsed == null || parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE
                ? null
                : parsed.intValue();
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static boolean equalsText(String left, String right) {
        return hasText(left) && hasText(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    static String hmacHex(String algorithm, String secret, String payload) {
        if (!hasText(secret) || payload == null) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate provider signature.", exception);
        }
    }

    static boolean secureHexEquals(String expected, String supplied) {
        if (!hasText(expected) || !hasText(supplied)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                supplied.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    static String canonicalQuery(Map<String, ?> fields, String prefix, List<String> excludedNames) {
        return canonicalQuery(fields, prefix, excludedNames, StandardCharsets.UTF_8);
    }

    static String canonicalQuery(
            Map<String, ?> fields,
            String prefix,
            List<String> excludedNames,
            Charset charset) {
        List<String> names = new ArrayList<>(fields.keySet());
        names.removeIf(name -> name == null
                || prefix != null && !name.startsWith(prefix)
                || excludedNames.contains(name)
                || rawText(fields.get(name)).isBlank());
        names.sort(String::compareTo);
        StringBuilder payload = new StringBuilder();
        for (String name : names) {
            if (!payload.isEmpty()) {
                payload.append('&');
            }
            payload.append(URLEncoder.encode(name, charset));
            payload.append('=');
            payload.append(URLEncoder.encode(rawText(fields.get(name)), charset));
        }
        return payload.toString();
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawText(value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate provider event digest.", exception);
        }
    }

    static Instant epochMillis(Long value) {
        if (value == null || value <= 0) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}

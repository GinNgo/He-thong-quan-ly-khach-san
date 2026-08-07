package com.hotel.services.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

final class PaymentSignature {

    private PaymentSignature() {
    }

    static String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate payment signature.", exception);
        }
    }

    static boolean matches(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                supplied.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
}

package com.hotel.services.payment;

public record VnpayCallbackVerification(
        boolean valid,
        String responseCode,
        String message,
        VnpayCallbackData data) {

    public static VnpayCallbackVerification invalid(String responseCode, String message) {
        return new VnpayCallbackVerification(false, responseCode, message, null);
    }

    public static VnpayCallbackVerification valid(VnpayCallbackData data) {
        return new VnpayCallbackVerification(true, "00", "Verified", data);
    }
}

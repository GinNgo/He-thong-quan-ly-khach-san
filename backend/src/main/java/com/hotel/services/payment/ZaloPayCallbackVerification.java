package com.hotel.services.payment;

public record ZaloPayCallbackVerification(boolean valid, String message, ProviderCallbackData data) {

    public static ZaloPayCallbackVerification valid(ProviderCallbackData data) {
        return new ZaloPayCallbackVerification(true, "Valid", data);
    }

    public static ZaloPayCallbackVerification invalid(String message) {
        return new ZaloPayCallbackVerification(false, message, null);
    }
}

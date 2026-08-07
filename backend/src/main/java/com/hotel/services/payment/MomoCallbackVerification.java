package com.hotel.services.payment;

public record MomoCallbackVerification(boolean valid, String message, ProviderCallbackData data) {

    public static MomoCallbackVerification valid(ProviderCallbackData data) {
        return new MomoCallbackVerification(true, "Valid", data);
    }

    public static MomoCallbackVerification invalid(String message) {
        return new MomoCallbackVerification(false, message, null);
    }
}

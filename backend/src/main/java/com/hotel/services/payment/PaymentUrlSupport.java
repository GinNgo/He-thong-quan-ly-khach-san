package com.hotel.services.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class PaymentUrlSupport {

    private PaymentUrlSupport() {
    }

    static String appendQueryParameter(String url, String name, String value) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator
                + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

package com.hotel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZaloPayConfig {

    @Value("${payment.zalopay.app-id:}")
    private String appId;

    @Value("${payment.zalopay.key1:}")
    private String key1;

    @Value("${payment.zalopay.key2:}")
    private String key2;

    @Value("${payment.zalopay.create-url:https://sb-openapi.zalopay.vn/v2/create}")
    private String createUrl;

    @Value("${payment.zalopay.query-url:https://sb-openapi.zalopay.vn/v2/query}")
    private String queryUrl;

    @Value("${payment.zalopay.refund-url:https://sb-openapi.zalopay.vn/v2/refund}")
    private String refundUrl;

    @Value("${payment.zalopay.refund-query-url:https://sb-openapi.zalopay.vn/v2/query_refund}")
    private String refundQueryUrl;

    @Value("${payment.zalopay.redirect-url:http://localhost:4200/payment-result}")
    private String redirectUrl;

    @Value("${payment.zalopay.callback-url:http://localhost:8080/api/payments/zalopay-callback}")
    private String callbackUrl;

    public boolean isConfigured() {
        return hasText(appId) && hasText(key1) && hasText(key2)
                && hasText(createUrl) && hasText(queryUrl) && hasText(refundUrl)
                && hasText(refundQueryUrl) && hasText(redirectUrl) && hasText(callbackUrl);
    }

    public int requireAppId() {
        try {
            return Integer.parseInt(appId);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("ZaloPay app id is not configured correctly.", exception);
        }
    }

    public String getAppId() {
        return appId;
    }

    public String getKey1() {
        return key1;
    }

    public String getKey2() {
        return key2;
    }

    public String getCreateUrl() {
        return createUrl;
    }

    public String getQueryUrl() {
        return queryUrl;
    }

    public String getRefundUrl() {
        return refundUrl;
    }

    public String getRefundQueryUrl() {
        return refundQueryUrl;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

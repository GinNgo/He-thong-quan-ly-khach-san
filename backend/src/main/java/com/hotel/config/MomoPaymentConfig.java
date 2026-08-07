package com.hotel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MomoPaymentConfig {

    @Value("${payment.momo.partner-code:}")
    private String partnerCode;

    @Value("${payment.momo.access-key:}")
    private String accessKey;

    @Value("${payment.momo.secret-key:}")
    private String secretKey;

    @Value("${payment.momo.create-url:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String createUrl;

    @Value("${payment.momo.query-url:https://test-payment.momo.vn/v2/gateway/api/query}")
    private String queryUrl;

    @Value("${payment.momo.refund-url:https://test-payment.momo.vn/v2/gateway/api/refund}")
    private String refundUrl;

    @Value("${payment.momo.refund-query-url:https://test-payment.momo.vn/v2/gateway/api/refund/query}")
    private String refundQueryUrl;

    @Value("${payment.momo.redirect-url:http://localhost:4200/payment-result}")
    private String redirectUrl;

    @Value("${payment.momo.ipn-url:http://localhost:8080/api/payments/momo-ipn}")
    private String ipnUrl;

    @Value("${payment.momo.request-type:captureWallet}")
    private String requestType;

    public boolean isConfigured() {
        return hasText(partnerCode) && hasText(accessKey) && hasText(secretKey)
                && hasText(createUrl) && hasText(queryUrl) && hasText(refundUrl)
                && hasText(refundQueryUrl) && hasText(redirectUrl) && hasText(ipnUrl);
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
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

    public String getIpnUrl() {
        return ipnUrl;
    }

    public String getRequestType() {
        return requestType;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

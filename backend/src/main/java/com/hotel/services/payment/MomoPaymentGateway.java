package com.hotel.services.payment;

import com.hotel.config.MomoPaymentConfig;
import com.hotel.domain.payment.PaymentProvider;
import com.hotel.dtos.MomoCallbackRequest;
import com.hotel.entities.PaymentSession;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MomoPaymentGateway {

    private final MomoPaymentConfig config;
    private final RestClient restClient;

    public MomoPaymentGateway(MomoPaymentConfig config, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.restClient = restClientBuilder.build();
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public String refundReference(String refundPublicId) {
        if (refundPublicId == null || refundPublicId.isBlank()) {
            throw new IllegalArgumentException("Refund public id is required.");
        }
        return "MOMO-R-" + refundPublicId.trim();
    }

    public String createPaymentUrl(PaymentSession session, LocalDateTime createdAt) {
        if (!isConfigured()) {
            throw new IllegalStateException("MoMo Test is not configured.");
        }

        long amount = session.getExpectedAmount().longValueExact();
        String orderInfo = "Thanh toan dat phong " + session.getReservation().getId();
        String redirectUrl = PaymentUrlSupport.appendQueryParameter(
                PaymentUrlSupport.appendQueryParameter(config.getRedirectUrl(), "session", session.getPublicId()),
                "provider",
                PaymentProvider.MOMO.name());
        String extraData = "";
        String requestId = session.getPublicId();
        String signature = PaymentSignature.hmacSha256(config.getSecretKey(), createSignaturePayload(
                amount,
                extraData,
                config.getIpnUrl(),
                session.getProviderReference(),
                orderInfo,
                redirectUrl,
                requestId));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("partnerCode", config.getPartnerCode());
        request.put("requestType", config.getRequestType());
        request.put("ipnUrl", config.getIpnUrl());
        request.put("redirectUrl", redirectUrl);
        request.put("orderId", session.getProviderReference());
        request.put("amount", amount);
        request.put("orderInfo", orderInfo);
        request.put("requestId", requestId);
        request.put("extraData", extraData);
        request.put("signature", signature);
        request.put("lang", "vi");

        Map<String, Object> response = restClient.post()
                .uri(config.getCreateUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return validateCreateResponse(response, amount, session.getProviderReference(), requestId);
    }

    public MomoCallbackVerification verifyCallback(MomoCallbackRequest request) {
        if (!isConfigured()) {
            return MomoCallbackVerification.invalid("MoMo is not configured");
        }
        if (request == null || !hasText(request.getSignature()) || !hasText(request.getPartnerCode())
                || !hasText(request.getOrderId()) || !hasText(request.getRequestId())
                || request.getAmount() == null || request.getResultCode() == null
                || request.getTransId() == null || request.getResponseTime() == null) {
            return MomoCallbackVerification.invalid("Invalid callback payload");
        }
        if (!config.getPartnerCode().equals(request.getPartnerCode())) {
            return MomoCallbackVerification.invalid("Invalid partner code");
        }

        String expected = PaymentSignature.hmacSha256(config.getSecretKey(), callbackSignaturePayload(request));
        if (!PaymentSignature.matches(expected, request.getSignature())) {
            return MomoCallbackVerification.invalid("Invalid callback signature");
        }

        return MomoCallbackVerification.valid(new ProviderCallbackData(
                PaymentProvider.MOMO,
                request.getOrderId(),
                Long.toString(request.getTransId()),
                BigDecimal.valueOf(request.getAmount()),
                request.getResultCode() == 0,
                "MOMO_" + request.getResultCode()));
    }

    public ProviderTransactionQueryResult queryTransaction(String orderId, String requestId) {
        requireConfigured();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("partnerCode", config.getPartnerCode());
        request.put("requestId", requestId);
        request.put("orderId", orderId);
        request.put("signature", PaymentSignature.hmacSha256(
                config.getSecretKey(),
                querySignaturePayload(orderId, requestId)));
        request.put("lang", "vi");

        Map<String, Object> response = restClient.post()
                .uri(config.getQueryUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return parseTransactionQueryResponse(response, orderId, requestId);
    }

    public ProviderRefundResult requestRefund(ProviderRefundCommand command) {
        requireConfigured();
        long amount = amount(command.amount());
        long transactionId = transactionId(command.originalProviderTransactionId());
        String description = nullToEmpty(command.description());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("partnerCode", config.getPartnerCode());
        request.put("orderId", command.providerRefundReference());
        request.put("requestId", command.requestId());
        request.put("amount", amount);
        request.put("transId", transactionId);
        request.put("lang", "vi");
        request.put("description", description);
        request.put("signature", PaymentSignature.hmacSha256(
                config.getSecretKey(),
                refundSignaturePayload(
                        command.providerRefundReference(),
                        command.requestId(),
                        amount,
                        transactionId,
                        description)));

        Map<String, Object> response = restClient.post()
                .uri(config.getRefundUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return parseRefundResponse(response, command.providerRefundReference(), command.requestId(), amount);
    }

    public ProviderRefundResult queryRefund(ProviderRefundQuery query) {
        requireConfigured();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("partnerCode", config.getPartnerCode());
        request.put("requestId", query.requestId());
        request.put("orderId", query.providerRefundReference());
        request.put("lang", "vi");
        request.put("signature", PaymentSignature.hmacSha256(
                config.getSecretKey(),
                querySignaturePayload(query.providerRefundReference(), query.requestId())));

        Map<String, Object> response = restClient.post()
                .uri(config.getRefundQueryUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return parseRefundQueryResponse(response, query);
    }

    String querySignaturePayload(String orderId, String requestId) {
        return "accessKey=" + config.getAccessKey()
                + "&orderId=" + orderId
                + "&partnerCode=" + config.getPartnerCode()
                + "&requestId=" + requestId;
    }

    String refundSignaturePayload(
            String orderId,
            String requestId,
            long amount,
            long transactionId,
            String description) {
        return "accessKey=" + config.getAccessKey()
                + "&amount=" + amount
                + "&description=" + description
                + "&orderId=" + orderId
                + "&partnerCode=" + config.getPartnerCode()
                + "&requestId=" + requestId
                + "&transId=" + transactionId;
    }

    String createSignaturePayload(
            long amount,
            String extraData,
            String ipnUrl,
            String orderId,
            String orderInfo,
            String redirectUrl,
            String requestId) {
        return "accessKey=" + config.getAccessKey()
                + "&amount=" + amount
                + "&extraData=" + nullToEmpty(extraData)
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + config.getPartnerCode()
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + config.getRequestType();
    }

    String callbackSignaturePayload(MomoCallbackRequest request) {
        return "accessKey=" + config.getAccessKey()
                + "&amount=" + request.getAmount()
                + "&extraData=" + nullToEmpty(request.getExtraData())
                + "&message=" + nullToEmpty(request.getMessage())
                + "&orderId=" + nullToEmpty(request.getOrderId())
                + "&orderInfo=" + nullToEmpty(request.getOrderInfo())
                + "&orderType=" + nullToEmpty(request.getOrderType())
                + "&partnerCode=" + nullToEmpty(request.getPartnerCode())
                + "&payType=" + nullToEmpty(request.getPayType())
                + "&requestId=" + nullToEmpty(request.getRequestId())
                + "&responseTime=" + request.getResponseTime()
                + "&resultCode=" + request.getResultCode()
                + "&transId=" + request.getTransId();
    }

    private String validateCreateResponse(
            Map<String, Object> response,
            long amount,
            String orderId,
            String requestId) {
        if (response == null) {
            throw new IllegalStateException("MoMo returned an empty create-order response.");
        }
        int resultCode = number(response.get("resultCode")).intValue();
        String payUrl = string(response.get("payUrl"));
        String partnerCode = string(response.get("partnerCode"));
        String signature = string(response.get("signature"));
        long responseTime = number(response.get("responseTime")).longValue();
        if (resultCode != 0 || !hasText(payUrl)) {
            throw new IllegalStateException("MoMo rejected payment session: " + resultCode);
        }
        if (!config.getPartnerCode().equals(partnerCode)) {
            throw new IllegalStateException("MoMo create-order response has an invalid partner code.");
        }

        String responsePayload = "accessKey=" + config.getAccessKey()
                + "&amount=" + amount
                + "&orderId=" + orderId
                + "&partnerCode=" + partnerCode
                + "&payUrl=" + payUrl
                + "&requestId=" + requestId
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode;
        String expected = PaymentSignature.hmacSha256(config.getSecretKey(), responsePayload);
        if (!PaymentSignature.matches(expected, signature)) {
            throw new IllegalStateException("MoMo create-order response signature is invalid.");
        }
        return payUrl;
    }

    private ProviderTransactionQueryResult parseTransactionQueryResponse(
            Map<String, Object> response,
            String orderId,
            String requestId) {
        requireResponse(response);
        requireMatches(response, "partnerCode", config.getPartnerCode(), "partner code");
        requireMatches(response, "orderId", orderId, "order id");
        requireMatches(response, "requestId", requestId, "request id");
        int resultCode = number(response.get("resultCode")).intValue();
        ProviderOperationStatus status = status(resultCode);
        BigDecimal amount = response.containsKey("amount") && response.get("amount") != null
                ? BigDecimal.valueOf(number(response.get("amount")).longValue())
                : null;
        String transactionId = response.containsKey("transId") && response.get("transId") != null
                ? Long.toString(number(response.get("transId")).longValue())
                : null;
        validateSuccessfulResult(status, amount, transactionId, "transaction query");
        return new ProviderTransactionQueryResult(
                PaymentProvider.MOMO,
                status,
                orderId,
                transactionId,
                amount,
                Integer.toString(resultCode),
                string(response.get("message")));
    }

    private ProviderRefundResult parseRefundResponse(
            Map<String, Object> response,
            String refundReference,
            String requestId,
            long requestedAmount) {
        requireResponse(response);
        requireMatches(response, "partnerCode", config.getPartnerCode(), "partner code");
        requireMatches(response, "orderId", refundReference, "refund order id");
        requireMatches(response, "requestId", requestId, "request id");
        if (response.containsKey("amount") && number(response.get("amount")).longValue() != requestedAmount) {
            throw new IllegalStateException("MoMo refund response amount does not match the persisted request.");
        }
        int resultCode = number(response.get("resultCode")).intValue();
        ProviderOperationStatus status = status(resultCode);
        String transactionId = response.containsKey("transId") && response.get("transId") != null
                ? Long.toString(number(response.get("transId")).longValue())
                : null;
        validateSuccessfulResult(status, BigDecimal.valueOf(requestedAmount), transactionId, "refund");
        return new ProviderRefundResult(
                PaymentProvider.MOMO,
                status,
                refundReference,
                transactionId,
                BigDecimal.valueOf(requestedAmount),
                Integer.toString(resultCode),
                string(response.get("message")));
    }

    private ProviderRefundResult parseRefundQueryResponse(
            Map<String, Object> response,
            ProviderRefundQuery query) {
        requireResponse(response);
        requireMatches(response, "partnerCode", config.getPartnerCode(), "partner code");
        requireMatches(response, "orderId", query.providerRefundReference(), "refund order id");
        requireMatches(response, "requestId", query.requestId(), "request id");
        int resultCode = number(response.get("resultCode")).intValue();
        Map<String, Object> matchingRefund = matchingRefund(response.get("refundTrans"), query.providerRefundReference());
        if (matchingRefund == null) {
            if (status(resultCode) == ProviderOperationStatus.PENDING) {
                return refundResult(query, ProviderOperationStatus.PENDING, null, resultCode, response.get("message"));
            }
            throw new IllegalStateException("MoMo refund query response does not contain the requested refund reference.");
        }
        long amount = number(matchingRefund.get("amount")).longValue();
        if (query.expectedAmount() != null && query.expectedAmount().compareTo(BigDecimal.valueOf(amount)) != 0) {
            throw new IllegalStateException("MoMo refund query amount does not match the persisted request.");
        }
        int itemCode = number(matchingRefund.get("resultCode")).intValue();
        String transactionId = matchingRefund.containsKey("transId")
                ? Long.toString(number(matchingRefund.get("transId")).longValue())
                : null;
        ProviderOperationStatus itemStatus = status(itemCode);
        validateSuccessfulResult(itemStatus, BigDecimal.valueOf(amount), transactionId, "refund query");
        return refundResult(
                query,
                itemStatus,
                transactionId,
                itemCode,
                matchingRefund.get("message"));
    }

    private ProviderRefundResult refundResult(
            ProviderRefundQuery query,
            ProviderOperationStatus status,
            String transactionId,
            int responseCode,
            Object message) {
        return new ProviderRefundResult(
                PaymentProvider.MOMO,
                status,
                query.providerRefundReference(),
                transactionId,
                query.expectedAmount(),
                Integer.toString(responseCode),
                string(message));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> matchingRefund(Object value, String refundReference) {
        if (!(value instanceof List<?> refunds)) {
            return null;
        }
        for (Object refund : refunds) {
            if (refund instanceof Map<?, ?> raw
                    && refundReference.equals(string(raw.get("orderId")))) {
                return (Map<String, Object>) raw;
            }
        }
        return null;
    }

    private ProviderOperationStatus status(int resultCode) {
        if (resultCode == 0) {
            return ProviderOperationStatus.SUCCEEDED;
        }
        if (resultCode == 1000 || resultCode == 7000 || resultCode == 7002 || resultCode == 9000) {
            return ProviderOperationStatus.PENDING;
        }
        return ProviderOperationStatus.FAILED;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("MoMo Test is not configured.");
        }
    }

    private void requireResponse(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("MoMo returned an empty provider response.");
        }
    }

    private void requireMatches(Map<String, Object> response, String field, String expected, String label) {
        if (!expected.equals(string(response.get(field)))) {
            throw new IllegalStateException("MoMo response has an invalid " + label + ".");
        }
    }

    private void validateSuccessfulResult(
            ProviderOperationStatus status,
            BigDecimal amount,
            String transactionId,
            String operation) {
        if (status != ProviderOperationStatus.SUCCEEDED) {
            return;
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("MoMo " + operation + " returned an invalid amount.");
        }
        try {
            if (Long.parseLong(transactionId) <= 0) {
                throw new NumberFormatException("non-positive");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("MoMo " + operation + " returned an invalid transaction id.", exception);
        }
    }

    private long amount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive.");
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Refund amount must be a whole VND amount.", exception);
        }
    }

    private long transactionId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MoMo transaction id is invalid.", exception);
        }
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return Long.parseLong(string(value));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("MoMo returned an invalid numeric value.", exception);
        }
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

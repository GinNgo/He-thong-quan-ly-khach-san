package com.hotel.services.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.config.ZaloPayConfig;
import com.hotel.domain.payment.PaymentProvider;
import com.hotel.dtos.ZaloPayCallbackRequest;
import com.hotel.entities.PaymentSession;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ZaloPayPaymentGateway {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ZaloPayConfig config;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ZaloPayPaymentGateway(
            ZaloPayConfig config,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public String refundReference(String refundPublicId, LocalDateTime requestedAt) {
        if (refundPublicId == null || refundPublicId.isBlank() || requestedAt == null) {
            throw new IllegalArgumentException("Refund public id and requested time are required.");
        }
        String suffix = refundPublicId.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (suffix.length() > 24) {
            suffix = suffix.substring(0, 24);
        }
        return requestedAt.atZone(VIETNAM_ZONE).format(DateTimeFormatter.ofPattern("yyMMdd"))
                + "_" + config.getAppId() + "_" + suffix;
    }

    public String createPaymentUrl(PaymentSession session, LocalDateTime createdAt) {
        if (!isConfigured()) {
            throw new IllegalStateException("ZaloPay Sandbox is not configured.");
        }

        int appId = config.requireAppId();
        long amount = session.getExpectedAmount().longValueExact();
        long appTime = createdAt.atZone(VIETNAM_ZONE).toInstant().toEpochMilli();
        String appUser = "customer-" + session.getOwner().getId();
        String item = "[]";
        String redirectUrl = PaymentUrlSupport.appendQueryParameter(
                PaymentUrlSupport.appendQueryParameter(config.getRedirectUrl(), "session", session.getPublicId()),
                "provider",
                PaymentProvider.ZALOPAY.name());
        String embedData = writeJson(Map.of("redirecturl", redirectUrl));
        String macInput = createMacPayload(
                appId,
                session.getProviderReference(),
                appUser,
                amount,
                appTime,
                embedData,
                item);

        MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("app_id", Integer.toString(appId));
        request.add("app_user", appUser);
        request.add("app_trans_id", session.getProviderReference());
        request.add("app_time", Long.toString(appTime));
        request.add("expire_duration_seconds", Long.toString(expirySeconds(createdAt, session.getExpiresAt())));
        request.add("amount", Long.toString(amount));
        request.add("description", "Thanh toan dat phong " + session.getReservation().getId());
        request.add("callback_url", config.getCallbackUrl());
        request.add("item", item);
        request.add("embed_data", embedData);
        request.add("bank_code", "");
        request.add("mac", PaymentSignature.hmacSha256(config.getKey1(), macInput));

        Map<String, Object> response = restClient.post()
                .uri(config.getCreateUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null) {
            throw new IllegalStateException("ZaloPay returned an empty create-order response.");
        }
        int returnCode = number(response.get("return_code")).intValue();
        String orderUrl = string(response.get("order_url"));
        if (returnCode != 1 || orderUrl.isBlank()) {
            throw new IllegalStateException("ZaloPay rejected payment session: " + returnCode);
        }
        return orderUrl;
    }

    public ZaloPayCallbackVerification verifyCallback(ZaloPayCallbackRequest request) {
        if (!isConfigured()) {
            return ZaloPayCallbackVerification.invalid("ZaloPay is not configured");
        }
        if (request == null || request.getType() == null || request.getType() != 1
                || !hasText(request.getData()) || !hasText(request.getMac())) {
            return ZaloPayCallbackVerification.invalid("Invalid callback payload");
        }
        String expected = PaymentSignature.hmacSha256(config.getKey2(), request.getData());
        if (!PaymentSignature.matches(expected, request.getMac())) {
            return ZaloPayCallbackVerification.invalid("Invalid callback signature");
        }

        try {
            JsonNode data = objectMapper.readTree(request.getData());
            if (data.path("app_id").asInt() != config.requireAppId()) {
                return ZaloPayCallbackVerification.invalid("Invalid app id");
            }
            String reference = data.path("app_trans_id").asText();
            String transactionId = data.path("zp_trans_id").asText();
            long amount = data.path("amount").asLong(-1);
            if (!hasText(reference) || !hasText(transactionId) || amount < 0) {
                return ZaloPayCallbackVerification.invalid("Invalid callback data");
            }
            return ZaloPayCallbackVerification.valid(new ProviderCallbackData(
                    PaymentProvider.ZALOPAY,
                    reference,
                    transactionId,
                    BigDecimal.valueOf(amount),
                    true,
                    null));
        } catch (JsonProcessingException exception) {
            return ZaloPayCallbackVerification.invalid("Invalid callback data");
        }
    }

    public ProviderTransactionQueryResult queryTransaction(String appTransId) {
        requireConfigured();
        int appId = config.requireAppId();
        MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("app_id", Integer.toString(appId));
        request.add("app_trans_id", appTransId);
        request.add("mac", PaymentSignature.hmacSha256(
                config.getKey1(),
                queryMacPayload(appId, appTransId)));
        Map<String, Object> response = restClient.post()
                .uri(config.getQueryUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response == null) {
            throw new IllegalStateException("ZaloPay returned an empty query response.");
        }
        int returnCode = number(response.get("return_code")).intValue();
        int subReturnCode = numberOrDefault(response.get("sub_return_code"), 0);
        ProviderOperationStatus status = status(returnCode, subReturnCode);
        BigDecimal amount = response.containsKey("amount") && response.get("amount") != null
                ? BigDecimal.valueOf(number(response.get("amount")).longValue())
                : null;
        String transactionId = response.containsKey("zp_trans_id") && response.get("zp_trans_id") != null
                ? Long.toString(number(response.get("zp_trans_id")).longValue())
                : null;
        validateSuccessfulQuery(status, amount, transactionId);
        return new ProviderTransactionQueryResult(
                PaymentProvider.ZALOPAY,
                status,
                appTransId,
                transactionId,
                amount,
                responseCode(returnCode, subReturnCode),
                string(response.get("return_message")));
    }

    public ProviderRefundResult requestRefund(ProviderRefundCommand command) {
        return requestRefund(command, System.currentTimeMillis());
    }

    public ProviderRefundResult requestRefund(ProviderRefundCommand command, long timestamp) {
        requireConfigured();
        int appId = config.requireAppId();
        long amount = amount(command.amount());
        String description = command.description() == null ? "" : command.description();
        MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("app_id", Integer.toString(appId));
        request.add("m_refund_id", command.providerRefundReference());
        request.add("zp_trans_id", command.originalProviderTransactionId());
        request.add("amount", Long.toString(amount));
        request.add("timestamp", Long.toString(timestamp));
        request.add("description", description);
        request.add("mac", PaymentSignature.hmacSha256(
                config.getKey1(),
                refundMacPayload(
                        appId,
                        command.originalProviderTransactionId(),
                        amount,
                        description,
                        timestamp)));

        Map<String, Object> response = restClient.post()
                .uri(config.getRefundUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return parseRefundResponse(response, command.providerRefundReference(), amount);
    }

    public ProviderRefundResult queryRefund(ProviderRefundQuery query) {
        return queryRefund(query, System.currentTimeMillis());
    }

    public ProviderRefundResult queryRefund(ProviderRefundQuery query, long timestamp) {
        requireConfigured();
        int appId = config.requireAppId();
        MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("app_id", Integer.toString(appId));
        request.add("m_refund_id", query.providerRefundReference());
        request.add("timestamp", Long.toString(timestamp));
        request.add("mac", PaymentSignature.hmacSha256(
                config.getKey1(),
                refundQueryMacPayload(appId, query.providerRefundReference(), timestamp)));

        Map<String, Object> response = restClient.post()
                .uri(config.getRefundQueryUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return parseRefundQueryResponse(response, query);
    }

    String createMacPayload(
            int appId,
            String appTransId,
            String appUser,
            long amount,
            long appTime,
            String embedData,
            String item) {
        return appId + "|" + appTransId + "|" + appUser + "|" + amount + "|" + appTime
                + "|" + embedData + "|" + item;
    }

    String queryMacPayload(int appId, String appTransId) {
        return appId + "|" + appTransId + "|" + config.getKey1();
    }

    String refundMacPayload(
            int appId,
            String providerTransactionId,
            long amount,
            String description,
            long timestamp) {
        return appId + "|" + providerTransactionId + "|" + amount + "|" + description + "|" + timestamp;
    }

    String refundQueryMacPayload(int appId, String refundReference, long timestamp) {
        return appId + "|" + refundReference + "|" + timestamp;
    }

    private long expirySeconds(LocalDateTime createdAt, LocalDateTime expiresAt) {
        long seconds = Duration.between(createdAt, expiresAt).getSeconds();
        return Math.max(300, Math.min(seconds, 2_592_000));
    }

    private String writeJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot build ZaloPay embed data.", exception);
        }
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return Long.parseLong(string(value));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("ZaloPay returned an invalid numeric value.", exception);
        }
    }

    private int numberOrDefault(Object value, int fallback) {
        return value == null ? fallback : number(value).intValue();
    }

    private ProviderRefundResult parseRefundResponse(
            Map<String, Object> response,
            String refundReference,
            long requestedAmount) {
        if (response == null) {
            throw new IllegalStateException("ZaloPay returned an empty refund response.");
        }
        int returnCode = number(response.get("return_code")).intValue();
        int subReturnCode = numberOrDefault(response.get("sub_return_code"), 0);
        String refundId = response.containsKey("refund_id") && response.get("refund_id") != null
                ? Long.toString(number(response.get("refund_id")).longValue())
                : null;
        return new ProviderRefundResult(
                PaymentProvider.ZALOPAY,
                refundSubmissionStatus(returnCode, subReturnCode),
                refundReference,
                refundId,
                BigDecimal.valueOf(requestedAmount),
                responseCode(returnCode, subReturnCode),
                string(response.get("return_message")));
    }

    private ProviderRefundResult parseRefundQueryResponse(
            Map<String, Object> response,
            ProviderRefundQuery query) {
        if (response == null) {
            throw new IllegalStateException("ZaloPay returned an empty refund query response.");
        }
        int returnCode = number(response.get("return_code")).intValue();
        int subReturnCode = numberOrDefault(response.get("sub_return_code"), 0);
        return new ProviderRefundResult(
                PaymentProvider.ZALOPAY,
                status(returnCode, subReturnCode),
                query.providerRefundReference(),
                null,
                query.expectedAmount(),
                responseCode(returnCode, subReturnCode),
                string(response.get("return_message")));
    }

    private ProviderOperationStatus status(int returnCode, int subReturnCode) {
        if (returnCode == 1) {
            return ProviderOperationStatus.SUCCEEDED;
        }
        if (returnCode == 3 || subReturnCode == -1 || subReturnCode == -16) {
            return ProviderOperationStatus.PENDING;
        }
        if (returnCode == 2 && subReturnCode == -101) {
            return ProviderOperationStatus.NOT_FOUND;
        }
        return ProviderOperationStatus.FAILED;
    }

    private ProviderOperationStatus refundSubmissionStatus(int returnCode, int subReturnCode) {
        if (returnCode == 1 || returnCode == 3 || subReturnCode == -1 || subReturnCode == -16) {
            return ProviderOperationStatus.PENDING;
        }
        if (returnCode == 2 && subReturnCode == -101) {
            return ProviderOperationStatus.NOT_FOUND;
        }
        return ProviderOperationStatus.FAILED;
    }

    private String responseCode(int returnCode, int subReturnCode) {
        return returnCode + "/" + subReturnCode;
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

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("ZaloPay Sandbox is not configured.");
        }
    }

    private void validateSuccessfulQuery(
            ProviderOperationStatus status,
            BigDecimal amount,
            String transactionId) {
        if (status != ProviderOperationStatus.SUCCEEDED) {
            return;
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("ZaloPay query returned an invalid amount.");
        }
        try {
            if (Long.parseLong(transactionId) <= 0) {
                throw new NumberFormatException("non-positive");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("ZaloPay query returned an invalid transaction id.", exception);
        }
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

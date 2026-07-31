package com.hotel.paymentprovider.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ZaloPayPaymentProviderAdapter implements PaymentProviderAdapter {

    private final ObjectMapper objectMapper;

    public ZaloPayPaymentProviderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return "ZALOPAY";
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        String key2 = ProviderAdapterSupport.credential(request, "key2");
        if (!ProviderAdapterSupport.hasText(key2)) {
            return VerificationResult.rejectedResult(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        String rawData = ProviderAdapterSupport.rawText(request.payload().get("data"));
        String supplied = ProviderAdapterSupport.hasText(request.signature())
                ? request.signature()
                : ProviderAdapterSupport.text(request.payload().get("mac"));
        Integer type = ProviderAdapterSupport.intValue(request.payload().get("type"));
        String expected = ProviderAdapterSupport.hmacHex("HmacSHA256", key2, rawData);
        if (type == null || type != 1 || !ProviderAdapterSupport.secureHexEquals(expected, supplied)) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        Callback callback = callback(rawData);
        if (callback == null || !ProviderAdapterSupport.hasText(callback.transactionId())) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        return ProviderAdapterSupport.validateBindings(
                request, callback.merchant(), callback.amount(), callback.currency(), callback.reference());
    }

    @Override
    public NormalizedCallback normalize(VerificationRequest request) {
        Callback callback = callback(ProviderAdapterSupport.rawText(request.payload().get("data")));
        if (callback == null) {
            throw new IllegalArgumentException("ZaloPay callback data is invalid.");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("successful", true);
        metadata.put("type", ProviderAdapterSupport.intValue(request.payload().get("type")));
        return new NormalizedCallback(provider(), callback.eventId(), callback.transactionId(),
                callback.reference(), callback.amount(), callback.currency(), callback.occurredAt(), metadata);
    }

    private Callback callback(String rawData) {
        if (!ProviderAdapterSupport.hasText(rawData)) {
            return null;
        }
        try {
            JsonNode data = objectMapper.readTree(rawData);
            String transactionId = data.path("zp_trans_id").asText("");
            long amount = data.path("amount").asLong(-1);
            if (amount < 0) {
                return null;
            }
            Long occurredAt = data.hasNonNull("server_time")
                    ? data.path("server_time").asLong()
                    : data.hasNonNull("app_time") ? data.path("app_time").asLong() : null;
            return new Callback(
                    data.path("app_id").asText(""),
                    data.path("app_trans_id").asText(""),
                    transactionId,
                    transactionId,
                    BigDecimal.valueOf(amount),
                    "VND",
                    ProviderAdapterSupport.epochMillis(occurredAt));
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private record Callback(String merchant, String reference, String transactionId, String eventId,
                            BigDecimal amount, String currency, Instant occurredAt) {
    }
}

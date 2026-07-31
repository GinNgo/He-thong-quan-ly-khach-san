package com.hotel.paymentprovider.adapters;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MomoPaymentProviderAdapter implements PaymentProviderAdapter {

    @Override
    public String provider() {
        return "MOMO";
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        String accessKey = ProviderAdapterSupport.credential(request, "accessKey");
        String secretKey = ProviderAdapterSupport.credential(request, "secretKey");
        if (!ProviderAdapterSupport.hasText(accessKey) || !ProviderAdapterSupport.hasText(secretKey)) {
            return VerificationResult.rejectedResult(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        Callback callback = callback(request.payload());
        String supplied = ProviderAdapterSupport.hasText(request.signature())
                ? request.signature()
                : ProviderAdapterSupport.text(request.payload().get("signature"));
        String expected = ProviderAdapterSupport.hmacHex(
                "HmacSHA256", secretKey, signaturePayload(request.payload(), accessKey));
        if (!ProviderAdapterSupport.secureHexEquals(expected, supplied)) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        if (callback.resultCode() == null || !ProviderAdapterSupport.hasText(callback.eventId())) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        if (callback.successful() && !ProviderAdapterSupport.hasText(callback.transactionId())) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        return ProviderAdapterSupport.validateBindings(
                request, callback.merchant(), callback.amount(), callback.currency(), callback.reference());
    }

    @Override
    public NormalizedCallback normalize(VerificationRequest request) {
        Callback callback = callback(request.payload());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("successful", callback.successful());
        metadata.put("resultCode", callback.resultCode());
        metadata.put("message", ProviderAdapterSupport.text(request.payload().get("message")));
        return new NormalizedCallback(provider(), callback.eventId(), callback.transactionId(),
                callback.reference(), callback.amount(), callback.currency(), callback.occurredAt(), metadata);
    }

    String signaturePayload(Map<String, ?> payload, String accessKey) {
        return "accessKey=" + accessKey
                + "&amount=" + ProviderAdapterSupport.rawText(payload.get("amount"))
                + "&extraData=" + ProviderAdapterSupport.rawText(payload.get("extraData"))
                + "&message=" + ProviderAdapterSupport.rawText(payload.get("message"))
                + "&orderId=" + ProviderAdapterSupport.rawText(payload.get("orderId"))
                + "&orderInfo=" + ProviderAdapterSupport.rawText(payload.get("orderInfo"))
                + "&orderType=" + ProviderAdapterSupport.rawText(payload.get("orderType"))
                + "&partnerCode=" + ProviderAdapterSupport.rawText(payload.get("partnerCode"))
                + "&payType=" + ProviderAdapterSupport.rawText(payload.get("payType"))
                + "&requestId=" + ProviderAdapterSupport.rawText(payload.get("requestId"))
                + "&responseTime=" + ProviderAdapterSupport.rawText(payload.get("responseTime"))
                + "&resultCode=" + ProviderAdapterSupport.rawText(payload.get("resultCode"))
                + "&transId=" + ProviderAdapterSupport.rawText(payload.get("transId"));
    }

    private Callback callback(Map<String, ?> payload) {
        Integer resultCode = ProviderAdapterSupport.intValue(payload.get("resultCode"));
        String transactionId = ProviderAdapterSupport.text(payload.get("transId"));
        String requestId = ProviderAdapterSupport.text(payload.get("requestId"));
        Long responseTime = ProviderAdapterSupport.longValue(payload.get("responseTime"));
        String eventId = ProviderAdapterSupport.hasText(transactionId)
                ? transactionId
                : ProviderAdapterSupport.hasText(requestId) && responseTime != null
                ? "MOMO:" + ProviderAdapterSupport.sha256Hex(requestId + "|" + resultCode + "|" + responseTime)
                : "";
        return new Callback(
                ProviderAdapterSupport.text(payload.get("partnerCode")),
                ProviderAdapterSupport.text(payload.get("orderId")),
                transactionId,
                eventId,
                ProviderAdapterSupport.decimal(payload.get("amount")),
                "VND",
                ProviderAdapterSupport.epochMillis(responseTime),
                resultCode,
                resultCode != null && resultCode == 0);
    }

    private record Callback(String merchant, String reference, String transactionId, String eventId,
                            BigDecimal amount, String currency, Instant occurredAt, Integer resultCode,
                            boolean successful) {
    }
}

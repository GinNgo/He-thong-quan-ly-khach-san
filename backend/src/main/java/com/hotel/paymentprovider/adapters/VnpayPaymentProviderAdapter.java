package com.hotel.paymentprovider.adapters;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VnpayPaymentProviderAdapter implements PaymentProviderAdapter {

    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public String provider() {
        return "VNPAY";
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        String secret = ProviderAdapterSupport.credential(request, "hashSecret");
        if (!ProviderAdapterSupport.hasText(secret)) {
            return VerificationResult.rejectedResult(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        Map<String, ?> payload = request.payload();
        String supplied = ProviderAdapterSupport.hasText(request.signature())
                ? request.signature()
                : ProviderAdapterSupport.text(payload.get("vnp_SecureHash"));
        String canonical = ProviderAdapterSupport.canonicalQuery(
                payload, "vnp_", List.of("vnp_SecureHash", "vnp_SecureHashType"),
                StandardCharsets.US_ASCII);
        String expected = ProviderAdapterSupport.hmacHex("HmacSHA512", secret, canonical);
        if (!ProviderAdapterSupport.secureHexEquals(expected, supplied)) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }

        Callback callback = callback(payload, supplied);
        if (callback.amount() == null) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH);
        }
        if (!ProviderAdapterSupport.hasText(callback.responseCode())
                || !ProviderAdapterSupport.hasText(callback.transactionStatus())) {
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
        String supplied = ProviderAdapterSupport.hasText(request.signature())
                ? request.signature()
                : ProviderAdapterSupport.text(request.payload().get("vnp_SecureHash"));
        Callback callback = callback(request.payload(), supplied);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("successful", callback.successful());
        metadata.put("responseCode", callback.responseCode());
        metadata.put("transactionStatus", callback.transactionStatus());
        return new NormalizedCallback(provider(), callback.eventId(), callback.transactionId(),
                callback.reference(), callback.amount(), callback.currency(), callback.occurredAt(), metadata);
    }

    private Callback callback(Map<String, ?> payload, String suppliedSignature) {
        String amountValue = ProviderAdapterSupport.text(payload.get("vnp_Amount"));
        BigDecimal rawAmount = ProviderAdapterSupport.decimal(amountValue);
        BigDecimal amount = null;
        if (rawAmount != null) {
            try {
                amount = rawAmount.movePointLeft(2).setScale(0, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException ignored) {
                amount = null;
            }
        }
        String reference = ProviderAdapterSupport.text(payload.get("vnp_TxnRef"));
        String transactionId = ProviderAdapterSupport.text(payload.get("vnp_TransactionNo"));
        String responseCode = ProviderAdapterSupport.text(payload.get("vnp_ResponseCode"));
        String transactionStatus = ProviderAdapterSupport.text(payload.get("vnp_TransactionStatus"));
        boolean successful = "00".equals(responseCode) && "00".equals(transactionStatus);
        String eventId = ProviderAdapterSupport.hasText(transactionId)
                ? transactionId
                : "VNPAY:" + ProviderAdapterSupport.sha256Hex(reference + "|" + responseCode + "|"
                + transactionStatus + "|" + ProviderAdapterSupport.text(payload.get("vnp_PayDate"))
                + "|" + suppliedSignature);
        return new Callback(
                ProviderAdapterSupport.text(payload.get("vnp_TmnCode")),
                reference,
                transactionId,
                eventId,
                amount,
                ProviderAdapterSupport.text(payload.get("vnp_CurrCode")),
                parseTime(ProviderAdapterSupport.text(payload.get("vnp_PayDate"))),
                responseCode,
                transactionStatus,
                successful);
    }

    private Instant parseTime(String value) {
        if (!ProviderAdapterSupport.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, VNPAY_TIME).atZone(VIETNAM_ZONE).toInstant();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private record Callback(String merchant, String reference, String transactionId, String eventId,
                            BigDecimal amount, String currency, Instant occurredAt, String responseCode,
                            String transactionStatus, boolean successful) {
    }
}

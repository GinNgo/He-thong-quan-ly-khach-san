package com.hotel.paymentprovider.adapters;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SimulatorPaymentProviderAdapter implements PaymentProviderAdapter {

    private static final Set<String> STATUSES = Set.of(
            "SUCCESS", "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED");

    @Override
    public String provider() {
        return "SIMULATOR";
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        String signingSecret = ProviderAdapterSupport.credential(request, "signingSecret");
        if (!ProviderAdapterSupport.hasText(signingSecret)) {
            return VerificationResult.rejectedResult(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        String supplied = ProviderAdapterSupport.hasText(request.signature())
                ? request.signature()
                : ProviderAdapterSupport.text(request.payload().get("signature"));
        String expected = ProviderAdapterSupport.hmacHex(
                "HmacSHA256", signingSecret,
                ProviderAdapterSupport.canonicalQuery(request.payload(), null, List.of("signature")));
        if (!ProviderAdapterSupport.secureHexEquals(expected, supplied)) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        Callback callback = callback(request.payload());
        if (!ProviderAdapterSupport.hasText(callback.eventId()) || !STATUSES.contains(callback.status())) {
            return VerificationResult.rejectedResult(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
        return ProviderAdapterSupport.validateBindings(
                request, callback.merchant(), callback.amount(), callback.currency(), callback.reference());
    }

    @Override
    public NormalizedCallback normalize(VerificationRequest request) {
        Callback callback = callback(request.payload());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("successful", callback.successful());
        metadata.put("status", callback.status());
        return new NormalizedCallback(provider(), callback.eventId(), callback.transactionId(),
                callback.reference(), callback.amount(), callback.currency(), callback.occurredAt(), metadata);
    }

    private Callback callback(Map<String, ?> payload) {
        String status = ProviderAdapterSupport.text(payload.get("status")).toUpperCase(Locale.ROOT);
        return new Callback(
                ProviderAdapterSupport.text(payload.get("merchantId")),
                ProviderAdapterSupport.text(payload.get("reference")),
                ProviderAdapterSupport.text(payload.get("transactionId")),
                ProviderAdapterSupport.text(payload.get("eventId")),
                ProviderAdapterSupport.decimal(payload.get("amount")),
                ProviderAdapterSupport.text(payload.get("currency")),
                instant(ProviderAdapterSupport.text(payload.get("occurredAt"))),
                status,
                "SUCCESS".equals(status) || "SUCCEEDED".equals(status));
    }

    private Instant instant(String value) {
        if (!ProviderAdapterSupport.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private record Callback(String merchant, String reference, String transactionId, String eventId,
                            BigDecimal amount, String currency, Instant occurredAt, String status,
                            boolean successful) {
    }
}

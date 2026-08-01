package com.hotel.paymentprovider.refund;

import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RefundProviderGateway {

    private final PaymentProviderAdapterRegistry paymentAdapters;
    private final Map<String, RefundProviderClient> refundClients;

    public RefundProviderGateway(
            PaymentProviderAdapterRegistry paymentAdapters,
            List<RefundProviderClient> refundClients) {
        this.paymentAdapters = paymentAdapters;
        Map<String, RefundProviderClient> indexed = new LinkedHashMap<>();
        for (RefundProviderClient client : refundClients) {
            String provider = normalize(client.provider());
            if (indexed.putIfAbsent(provider, client) != null) {
                throw new IllegalStateException("Refund provider clients must have unique provider names.");
            }
        }
        this.refundClients = Map.copyOf(indexed);
    }

    public PreparedAttempt prepare(PrepareCommand command) {
        validatePrepare(command);
        String provider = normalize(command.provider());
        paymentAdapters.require(provider);
        RefundProviderClient client = refundClients.get(provider);
        if (client == null) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE,
                    "No outbound refund adapter is registered for " + provider + ".");
        }
        String reference = "REFUND-" + command.refundPublicId() + '-' + command.attemptNumber();
        RefundProviderClient.PreparedRefund prepared = client.prepare(new RefundProviderClient.PrepareRefund(
                command.environment(), command.refundPublicId(), VndMoney.of(command.amount()).amount(),
                command.currency(), reference, command.merchantId(), command.credentials(), command.endpoint()));
        return new PreparedAttempt(provider, command.environment(), prepared.providerReference(),
                prepared.dispatched(), prepared.externalStatus());
    }

    public PaymentProviderAdapter.NormalizedCallback normalizeCallback(
            String providerValue,
            String signature,
            Map<String, ?> payload,
            Instant receivedAt) {
        String provider = normalize(providerValue);
        try {
            return paymentAdapters.require(provider).normalize(new PaymentProviderAdapter.VerificationRequest(
                    null, null, null, null, null, null, null, null,
                    signature, payload, Map.of(), null, receivedAt));
        } catch (FinancialException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }
    }

    public VerifiedCallback verify(VerifyCommand command) {
        if (command == null || command.normalizedCallback() == null) {
            throw new IllegalArgumentException("Refund callback verification command is required.");
        }
        String provider = normalize(command.provider());
        PaymentProviderAdapter.NormalizedCallback callback = command.normalizedCallback();
        PaymentProviderAdapter.VerificationResult verification = paymentAdapters.require(provider).verify(
                new PaymentProviderAdapter.VerificationRequest(
                        null, command.expectedMerchantId(), command.expectedAmount(), callback.amount(),
                        command.expectedCurrency(), callback.currency(), command.expectedReference(), callback.reference(),
                        command.signature(), command.payload(), command.credentials(), command.expiresAt(), command.receivedAt()));
        return new VerifiedCallback(callback, verification.accepted(), verification.errorCode(), verification.retryable());
    }

    private void validatePrepare(PrepareCommand command) {
        if (command == null || command.environment() == null) {
            throw new IllegalArgumentException("Refund provider preparation is required.");
        }
        if (command.attemptNumber() < 1) throw new IllegalArgumentException("attemptNumber must be positive.");
        requireText(command.refundPublicId(), "refundPublicId");
        requireText(command.provider(), "provider");
        if (!"VND".equalsIgnoreCase(requireText(command.currency(), "currency"))) {
            throw new FinancialException(FinancialErrorCode.INVALID_CURRENCY);
        }
        if (VndMoney.of(command.amount()).amount().signum() <= 0) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT);
        }
    }

    private String normalize(String value) {
        return requireText(value, "provider").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }

    public record PrepareCommand(
            String refundPublicId,
            int attemptNumber,
            String provider,
            PaymentEnvironment environment,
            BigDecimal amount,
            String currency,
            String merchantId,
            Map<String, ?> credentials,
            URI endpoint) {
    }

    public record PreparedAttempt(
            String provider,
            PaymentEnvironment environment,
            String providerReference,
            boolean dispatched,
            String externalStatus) {
    }

    public record VerifyCommand(
            String provider,
            String expectedMerchantId,
            BigDecimal expectedAmount,
            String expectedCurrency,
            String expectedReference,
            String signature,
            Map<String, ?> payload,
            Map<String, ?> credentials,
            Instant expiresAt,
            Instant receivedAt,
            PaymentProviderAdapter.NormalizedCallback normalizedCallback) {
    }

    public record VerifiedCallback(
            PaymentProviderAdapter.NormalizedCallback callback,
            boolean accepted,
            FinancialErrorCode errorCode,
            boolean retryable) {
    }
}

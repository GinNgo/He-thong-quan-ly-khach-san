package com.hotel.propertycommerce.payment;

import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.ProviderCredentials;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Component
public class PropertyPaymentCallbackCredentialsResolver {

    private final PropertyPaymentAttemptRepository attemptRepository;
    private final PaymentProviderAdapterRegistry adapterRegistry;
    private final PaymentEnvironmentGuard environmentGuard;
    private final Environment properties;

    public PropertyPaymentCallbackCredentialsResolver(
            PropertyPaymentAttemptRepository attemptRepository,
            PaymentProviderAdapterRegistry adapterRegistry,
            PaymentEnvironmentGuard environmentGuard,
            Environment properties) {
        this.attemptRepository = attemptRepository;
        this.adapterRegistry = adapterRegistry;
        this.environmentGuard = environmentGuard;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public CallbackContext resolve(String providerValue, Map<String, ?> payload) {
        String provider = normalize(providerValue);
        if (payload == null || payload.isEmpty()) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
        }

        String reference;
        try {
            PaymentProviderAdapter adapter = adapterRegistry.require(provider);
            reference = adapter.normalize(new PaymentProviderAdapter.VerificationRequest(
                    null, null, null, null, null, null, null, null,
                    null, payload, Map.of(), null, Instant.now())).reference();
        } catch (FinancialException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        if (reference == null || reference.isBlank()) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }

        PropertyPaymentAttempt attempt = attemptRepository.findByProviderAndReference(provider, reference.trim())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        ProviderCredentials credentials = credentials(provider);
        if (!credentials.complete()) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        environmentGuard.validate(attempt.getEnvironment(), provider, credentials);
        return new CallbackContext(attempt.getEnvironment(), credentials.merchantId(), credentials.secrets());
    }

    private ProviderCredentials credentials(String provider) {
        return switch (provider) {
            case "SIMULATOR" -> credentials(
                    property("payment.property.simulator.merchant-id", "PROPERTY-SIMULATOR"),
                    Map.of("signingSecret", property("payment.demo.signing-secret", property("jwt.secret", ""))),
                    property("payment.demo.base-url", "http://localhost:4200/payment-simulator"));
            case "VNPAY" -> credentials(
                    property("payment.vnpay.tmn-code", ""),
                    Map.of("hashSecret", property("payment.vnpay.hash-secret", "")),
                    property("payment.vnpay.url", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"));
            case "MOMO" -> credentials(
                    property("payment.momo.partner-code", ""),
                    Map.of(
                            "accessKey", property("payment.momo.access-key", ""),
                            "secretKey", property("payment.momo.secret-key", "")),
                    property("payment.momo.create-url", "https://test-payment.momo.vn/v2/gateway/api/create"));
            case "ZALOPAY" -> credentials(
                    property("payment.zalopay.app-id", ""),
                    Map.of("key2", property("payment.zalopay.key2", "")),
                    property("payment.zalopay.create-url", "https://sb-openapi.zalopay.vn/v2/create"));
            default -> throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        };
    }

    private ProviderCredentials credentials(String merchantId, Map<String, ?> secrets, String endpoint) {
        try {
            return new ProviderCredentials(merchantId, secrets, URI.create(endpoint));
        } catch (IllegalArgumentException exception) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    private String property(String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalize(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        return provider.trim().toUpperCase(Locale.ROOT);
    }

    public record CallbackContext(
            PaymentEnvironment environment,
            String merchantId,
            Map<String, ?> credentials) {
    }
}

package com.hotel.paymentprovider.refund;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.ProviderCredentials;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.config.PlatformMerchantCredentialResolver.ResolvedMerchantCredentials;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

@Component
public class RefundProviderCredentialsResolver {

    private final Environment properties;
    private final PaymentEnvironmentGuard environmentGuard;
    private final PlatformPaymentConfigurationService platformConfigurationService;

    public RefundProviderCredentialsResolver(
            Environment properties,
            PaymentEnvironmentGuard environmentGuard,
            PlatformPaymentConfigurationService platformConfigurationService) {
        this.properties = properties;
        this.environmentGuard = environmentGuard;
        this.platformConfigurationService = platformConfigurationService;
    }

    public Context property(String providerValue, PaymentEnvironment environment) {
        String provider = normalize(providerValue);
        ProviderCredentials credentials = propertyCredentials(provider);
        if (!credentials.complete()) throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        environmentGuard.validate(environment, provider, environment == PaymentEnvironment.SIMULATOR ? null : credentials);
        return new Context(environment, provider, credentials.merchantId(), credentials.secrets(), credentials.endpoint());
    }

    public Context propertyCallback(String providerValue) {
        String provider = normalize(providerValue);
        ProviderCredentials credentials = propertyCredentials(provider);
        if (!credentials.complete()) throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        return new Context(null, provider, credentials.merchantId(), credentials.secrets(), credentials.endpoint());
    }

    public Context platform(String providerValue) {
        String provider = normalize(providerValue);
        PlatformPaymentConfigurationService.ReadyConfiguration ready = platformConfigurationService.requireReady(provider);
        ResolvedMerchantCredentials credentials = ready.credentials();
        if (credentials == null || credentials.merchantId() == null) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        return new Context(ready.configuration().getEnvironment(), provider, credentials.merchantId(),
                credentials.secrets(), credentials.endpoint());
    }

    private ProviderCredentials propertyCredentials(String provider) {
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
                    Map.of("accessKey", property("payment.momo.access-key", ""),
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record Context(
            PaymentEnvironment environment,
            String provider,
            String merchantId,
            Map<String, ?> credentials,
            URI endpoint) {
    }
}

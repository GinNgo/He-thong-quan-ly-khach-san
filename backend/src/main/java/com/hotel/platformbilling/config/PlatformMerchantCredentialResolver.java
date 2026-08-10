package com.hotel.platformbilling.config;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.ProviderCredentials;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class PlatformMerchantCredentialResolver {

    private final Environment environment;

    public PlatformMerchantCredentialResolver(Environment environment) {
        this.environment = environment;
    }

    public ResolvedMerchantCredentials resolve(PlatformPaymentConfiguration configuration) {
        return resolveReference(configuration == null ? null : configuration.getSecretReference());
    }

    public ResolvedMerchantCredentials resolveReference(String secretReference) {
        if (secretReference != null && "internal://simulator".equalsIgnoreCase(secretReference.trim())) {
            String signingSecret = environment.getProperty(
                    "payment.demo.signing-secret",
                    environment.getProperty("jwt.secret", ""));
            if (signingSecret == null || signingSecret.isBlank()) {
                return new ResolvedMerchantCredentials(null, Map.of(), null);
            }
            return new ResolvedMerchantCredentials(
                    "PLATFORM-SIMULATOR",
                    Map.of("signingSecret", signingSecret.trim()),
                    null);
        }
        String prefix = environmentPrefix(secretReference);
        if (prefix == null) {
            return new ResolvedMerchantCredentials(null, Map.of(), null);
        }
        String merchantId = property(prefix, "MERCHANT_ID");
        String endpointValue = property(prefix, "ENDPOINT");
        Map<String, String> secrets = new LinkedHashMap<>();
        putIfPresent(secrets, "secret", property(prefix, "SECRET"));
        putIfPresent(secrets, "signingSecret", property(prefix, "SIGNING_SECRET"));
        putIfPresent(secrets, "accessKey", property(prefix, "ACCESS_KEY"));
        putIfPresent(secrets, "secretKey", property(prefix, "SECRET_KEY"));
        putIfPresent(secrets, "hashSecret", property(prefix, "HASH_SECRET"));
        putIfPresent(secrets, "key2", property(prefix, "KEY2"));
        if (prefix.contains("VNPAY")) {
            merchantId = firstPresent(merchantId, environment.getProperty("VNPAY_TMN_CODE"));
            endpointValue = firstPresent(endpointValue, environment.getProperty("VNPAY_URL"));
            putIfPresent(secrets, "hashSecret", firstPresent(
                    secrets.get("hashSecret"), environment.getProperty("VNPAY_HASH_SECRET")));
        }
        URI endpoint = endpointValue == null || endpointValue.isBlank() ? null : safeUri(endpointValue);
        return new ResolvedMerchantCredentials(merchantId, Map.copyOf(secrets), endpoint);
    }

    private String firstPresent(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary.trim()
                : fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private String property(String prefix, String suffix) {
        String underscore = prefix + '_' + suffix;
        String value = environment.getProperty(underscore);
        if (value == null || value.isBlank()) {
            value = environment.getProperty((prefix + '.' + suffix).toLowerCase(Locale.ROOT));
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String environmentPrefix(String secretReference) {
        if (secretReference == null || secretReference.isBlank()) {
            return null;
        }
        String normalized = secretReference.trim();
        if (normalized.regionMatches(true, 0, "env://", 0, 6)) {
            normalized = normalized.substring(6);
        } else if (normalized.regionMatches(true, 0, "env:", 0, 4)) {
            normalized = normalized.substring(4);
        } else {
            return null;
        }
        normalized = normalized.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z0-9_]+") ? normalized : null;
    }

    private URI safeUri(String value) {
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute() ? uri : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record ResolvedMerchantCredentials(String merchantId, Map<String, String> secrets, URI endpoint) {
        public ProviderCredentials guardCredentials() {
            return new ProviderCredentials(merchantId, secrets, endpoint);
        }

        public String maskedMerchant() {
            if (merchantId == null || merchantId.length() < 4) {
                return merchantId == null ? null : "****";
            }
            return "****" + merchantId.substring(merchantId.length() - 4);
        }

        @Override
        public String toString() {
            return "ResolvedMerchantCredentials[merchantId=<redacted>, secrets=<redacted>, endpoint="
                    + endpoint + ']';
        }
    }
}

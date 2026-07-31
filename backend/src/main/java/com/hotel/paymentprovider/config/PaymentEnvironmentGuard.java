package com.hotel.paymentprovider.config;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PaymentEnvironmentGuard {

    private final boolean simulatorEnabled;
    private final boolean sandboxEnabled;
    private final boolean productionEnabled;
    private final boolean productionApproved;
    private final boolean productionProfile;

    @Autowired
    public PaymentEnvironmentGuard(
            Environment environment,
            @Value("${payment.demo.enabled:false}") boolean simulatorEnabled,
            @Value("${payment.sandbox.enabled:true}") boolean sandboxEnabled,
            @Value("${payment.production.enabled:false}") boolean productionEnabled,
            @Value("${payment.production.approved:false}") boolean productionApproved) {
        this(environment, simulatorEnabled, sandboxEnabled, productionEnabled, productionApproved,
                environment != null && environment.matchesProfiles("production"));
    }

    public PaymentEnvironmentGuard(boolean simulatorEnabled, boolean sandboxEnabled,
                                   boolean productionEnabled, boolean productionApproved,
                                   boolean productionProfile) {
        this(null, simulatorEnabled, sandboxEnabled, productionEnabled, productionApproved, productionProfile);
    }

    private PaymentEnvironmentGuard(Environment ignored, boolean simulatorEnabled, boolean sandboxEnabled,
                                    boolean productionEnabled, boolean productionApproved,
                                    boolean productionProfile) {
        this.simulatorEnabled = simulatorEnabled;
        this.sandboxEnabled = sandboxEnabled;
        this.productionEnabled = productionEnabled;
        this.productionApproved = productionApproved;
        this.productionProfile = productionProfile;
    }

    public Readiness validate(PaymentEnvironment mode, String provider, ProviderCredentials credentials) {
        if (mode == null || provider == null || provider.isBlank()) {
            throw new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED);
        }
        List<String> blockers = new ArrayList<>();
        if (productionProfile && mode != PaymentEnvironment.PRODUCTION) {
            blockers.add("production_profile_requires_production_mode");
        }
        switch (mode) {
            case SIMULATOR -> {
                if (!simulatorEnabled) blockers.add("simulator_disabled");
            }
            case SANDBOX -> {
                if (!sandboxEnabled) blockers.add("sandbox_disabled");
                if (credentials == null || !credentials.complete()) blockers.add("sandbox_credentials_incomplete");
            }
            case PRODUCTION -> {
                if (!productionEnabled) blockers.add("production_disabled");
                if (!productionApproved) blockers.add("production_not_approved");
                if (credentials == null || !credentials.complete()) blockers.add("production_credentials_incomplete");
                if (credentials != null && credentials.merchantId() == null) blockers.add("merchant_identity_missing");
                if (credentials != null && credentials.endpoint() != null
                        && credentials.endpoint().getHost() != null
                        && credentials.endpoint().getHost().toLowerCase(Locale.ROOT).contains("sandbox")) {
                    blockers.add("production_endpoint_is_sandbox");
                }
            }
        }
        if (!blockers.isEmpty()) {
            FinancialErrorCode code = mode == PaymentEnvironment.PRODUCTION && !productionApproved
                    ? FinancialErrorCode.PRODUCTION_NOT_APPROVED
                    : FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED;
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            blockers.forEach(blocker -> fieldErrors.put(blocker, blocker));
            throw new FinancialException(code, code.defaultMessage(), fieldErrors, mode.name(), null);
        }
        return new Readiness(true, mode, provider, credentials == null ? null : credentials.maskedMerchant(), List.of());
    }

    public record ProviderCredentials(String merchantId, Map<String, ?> secrets, URI endpoint) {
        public boolean complete() {
            return merchantId != null && !merchantId.isBlank()
                    && secrets != null && !secrets.isEmpty()
                    && secrets.values().stream().allMatch(value -> value != null && !value.toString().isBlank());
        }

        public String maskedMerchant() {
            if (merchantId == null || merchantId.length() < 4) return "****";
            return "****" + merchantId.substring(merchantId.length() - 4);
        }
    }

    public record Readiness(boolean ready, PaymentEnvironment mode, String provider,
                            String maskedMerchant, List<String> blockers) {
    }

    public enum PaymentEnvironment {
        SIMULATOR,
        SANDBOX,
        PRODUCTION
    }
}

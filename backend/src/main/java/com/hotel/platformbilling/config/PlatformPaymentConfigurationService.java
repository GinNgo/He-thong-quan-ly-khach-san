package com.hotel.platformbilling.config;

import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.config.PlatformMerchantCredentialResolver.ResolvedMerchantCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PlatformPaymentConfigurationService {

    private final PlatformPaymentConfigurationRepository repository;
    private final PlatformMerchantCredentialResolver credentialResolver;
    private final PaymentEnvironmentGuard environmentGuard;
    private final PaymentProviderAdapterRegistry adapterRegistry;

    public PlatformPaymentConfigurationService(
            PlatformPaymentConfigurationRepository repository,
            PlatformMerchantCredentialResolver credentialResolver,
            PaymentEnvironmentGuard environmentGuard,
            PaymentProviderAdapterRegistry adapterRegistry) {
        this.repository = repository;
        this.credentialResolver = credentialResolver;
        this.environmentGuard = environmentGuard;
        this.adapterRegistry = adapterRegistry;
    }

    @Transactional
    public ConfigurationResponse configure(ConfigurationCommand command) {
        validate(command);
        String provider = normalizeCode(command.provider(), "provider");
        PaymentEnvironment mode = command.environment();
        adapterRegistry.require(provider);
        if (mode == PaymentEnvironment.PRODUCTION) {
            throw new FinancialException(FinancialErrorCode.PRODUCTION_NOT_APPROVED,
                    "Production platform merchant changes require a separate readiness approval.");
        }

        PlatformPaymentConfiguration configuration = repository.findByProviderAndEnvironment(provider, mode)
                .orElseGet(() -> PlatformPaymentConfiguration.create(provider, mode));
        ResolvedMerchantCredentials credentials = mode == PaymentEnvironment.SIMULATOR
                ? null
                : credentialResolver.resolveReference(command.secretReference());
        if (command.enabled()) {
            boolean anotherEnvironmentEnabled = repository
                    .findByProviderAndEnabledTrueOrderByEnvironmentAsc(provider).stream()
                    .anyMatch(item -> item.getEnvironment() != mode);
            if (anotherEnvironmentEnabled) {
                throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                        "Disable the current platform merchant environment before enabling another one.");
            }
            validateCallback(mode, command.callbackUrl());
            validateResolvedCredentials(mode, provider, credentials);
        }
        configuration.configure(
                command.enabled(),
                credentials == null ? null : credentials.maskedMerchant(),
                normalizeOptional(command.secretReference(), 500),
                normalizeOptional(command.bankName(), 160),
                normalizeOptional(command.bankAccountMasked(), 80),
                normalizeOptional(command.callbackUrl(), 1000));
        configuration = repository.saveAndFlush(configuration);
        return inspect(configuration);
    }

    @Transactional(readOnly = true)
    public ConfigurationResponse get(String provider, PaymentEnvironment environment) {
        PlatformPaymentConfiguration configuration = repository.findByProviderAndEnvironment(
                        normalizeCode(provider, "provider"), environment)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        return inspect(configuration);
    }

    @Transactional(readOnly = true)
    public List<ConfigurationResponse> list() {
        return repository.findAll().stream()
                .map(this::inspect)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReadyConfiguration requireReady(String provider) {
        String normalized = normalizeCode(provider, "provider");
        adapterRegistry.require(normalized);
        List<PlatformPaymentConfiguration> enabled = repository
                .findByProviderAndEnabledTrueOrderByEnvironmentAsc(normalized);
        if (enabled.isEmpty()) {
            throw new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED,
                    "No enabled platform merchant configuration is available for this provider.");
        }
        if (enabled.size() > 1) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Multiple platform merchant environments are enabled for the same provider.");
        }
        PlatformPaymentConfiguration configuration = enabled.get(0);
        ResolvedMerchantCredentials credentials = resolve(configuration);
        PaymentEnvironmentGuard.Readiness readiness = validateReadiness(configuration, credentials);
        return new ReadyConfiguration(configuration, credentials, readiness);
    }

    private ConfigurationResponse inspect(PlatformPaymentConfiguration configuration) {
        List<String> blockers = new ArrayList<>();
        boolean ready = false;
        if (!configuration.isEnabled()) {
            blockers.add("configuration_disabled");
        } else {
            try {
                adapterRegistry.require(configuration.getProvider());
                ResolvedMerchantCredentials credentials = resolve(configuration);
                validateReadiness(configuration, credentials);
                ready = true;
            } catch (FinancialException exception) {
                if (exception.fieldErrors().isEmpty()) {
                    blockers.add(exception.code().name());
                } else {
                    blockers.addAll(exception.fieldErrors().keySet());
                }
            }
        }
        return new ConfigurationResponse(
                configuration.getId(),
                configuration.getProvider(),
                configuration.getEnvironment(),
                configuration.isEnabled(),
                configuration.getMerchantReferenceMasked(),
                configuration.getSecretReference() != null && !configuration.getSecretReference().isBlank(),
                configuration.getBankName(),
                configuration.getBankAccountMasked(),
                configuration.getCallbackUrl(),
                configuration.productionApproved(),
                ready,
                List.copyOf(blockers));
    }

    private ResolvedMerchantCredentials resolve(PlatformPaymentConfiguration configuration) {
        return configuration.getEnvironment() == PaymentEnvironment.SIMULATOR
                ? null
                : credentialResolver.resolve(configuration);
    }

    private PaymentEnvironmentGuard.Readiness validateReadiness(
            PlatformPaymentConfiguration configuration,
            ResolvedMerchantCredentials credentials) {
        if (configuration.getEnvironment() == PaymentEnvironment.PRODUCTION
                && !configuration.productionApproved()) {
            throw new FinancialException(FinancialErrorCode.PRODUCTION_NOT_APPROVED);
        }
        validateCallback(configuration.getEnvironment(), configuration.getCallbackUrl());
        validateResolvedCredentials(configuration.getEnvironment(), configuration.getProvider(), credentials);
        return environmentGuard.validate(
                configuration.getEnvironment(),
                configuration.getProvider(),
                credentials == null ? null : credentials.guardCredentials());
    }

    private void validateResolvedCredentials(
            PaymentEnvironment mode,
            String provider,
            ResolvedMerchantCredentials credentials) {
        if (mode == PaymentEnvironment.SIMULATOR) {
            environmentGuard.validate(mode, provider, null);
            return;
        }
        if (credentials == null || credentials.endpoint() == null) {
            throw new FinancialException(
                    FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED,
                    "Platform merchant provider endpoint is not configured.",
                    Map.of("provider_endpoint_missing", "provider_endpoint_missing"),
                    mode.name(),
                    null);
        }
        environmentGuard.validate(mode, provider, credentials.guardCredentials());
    }

    private void validateCallback(PaymentEnvironment mode, String callbackUrl) {
        if (mode == PaymentEnvironment.SIMULATOR && (callbackUrl == null || callbackUrl.isBlank())) {
            return;
        }
        try {
            URI uri = URI.create(requireText(callbackUrl, "callbackUrl", 1000));
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("callbackUrl must use HTTPS.");
            }
        } catch (IllegalArgumentException exception) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Platform payment callback URL must be an absolute HTTPS URL.");
        }
    }

    private void validate(ConfigurationCommand command) {
        if (command == null || command.environment() == null) {
            throw new IllegalArgumentException("Provider and payment environment are required.");
        }
    }

    private String normalizeCode(String value, String field) {
        return requireText(value, field, 40).toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("value is too long.");
        }
        return normalized;
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return normalized;
    }

    public record ConfigurationCommand(
            String provider,
            PaymentEnvironment environment,
            boolean enabled,
            String secretReference,
            String bankName,
            String bankAccountMasked,
            String callbackUrl) {
    }

    public record ConfigurationResponse(
            Long id,
            String provider,
            PaymentEnvironment environment,
            boolean enabled,
            String merchantReferenceMasked,
            boolean secretConfigured,
            String bankName,
            String bankAccountMasked,
            String callbackUrl,
            boolean productionApproved,
            boolean ready,
            List<String> blockers) {
    }

    public record ReadyConfiguration(
            PlatformPaymentConfiguration configuration,
            ResolvedMerchantCredentials credentials,
            PaymentEnvironmentGuard.Readiness readiness) {
    }
}

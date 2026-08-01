package com.hotel.platformbilling;

import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import com.hotel.platformbilling.config.PlatformMerchantCredentialResolver;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationRepository;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformPaymentConfigurationServiceTest {

    @Mock private PlatformPaymentConfigurationRepository repository;
    @Mock private PlatformMerchantCredentialResolver credentialResolver;
    @Mock private PaymentProviderAdapterRegistry adapterRegistry;

    private PlatformPaymentConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new PlatformPaymentConfigurationService(
                repository,
                credentialResolver,
                new PaymentEnvironmentGuard(true, true, false, false, false),
                adapterRegistry);
    }

    @Test
    void storesOnlyMaskedMerchantMetadataAndReturnsSecretPresence() {
        when(adapterRegistry.require("MOMO")).thenReturn(mock(PaymentProviderAdapter.class));
        when(repository.findByProviderAndEnvironment("MOMO", PaymentEnvironment.SANDBOX))
                .thenReturn(Optional.empty());
        when(credentialResolver.resolveReference("env:PLATFORM_MOMO"))
                .thenReturn(new PlatformMerchantCredentialResolver.ResolvedMerchantCredentials(
                        "platform-merchant-7890",
                        Map.of("accessKey", "sandbox-access-value", "secretKey", "sandbox-secret-value"),
                        URI.create("https://sandbox.momo.example/pay")));
        when(credentialResolver.resolve(any(PlatformPaymentConfiguration.class)))
                .thenReturn(new PlatformMerchantCredentialResolver.ResolvedMerchantCredentials(
                        "platform-merchant-7890",
                        Map.of("accessKey", "sandbox-access-value", "secretKey", "sandbox-secret-value"),
                        URI.create("https://sandbox.momo.example/pay")));
        when(repository.saveAndFlush(any(PlatformPaymentConfiguration.class))).thenAnswer(invocation -> {
            PlatformPaymentConfiguration configuration = invocation.getArgument(0);
            ReflectionTestUtils.setField(configuration, "id", 11L);
            return configuration;
        });

        PlatformPaymentConfigurationService.ConfigurationResponse response = service.configure(
                new PlatformPaymentConfigurationService.ConfigurationCommand(
                        "momo",
                        PaymentEnvironment.SANDBOX,
                        true,
                        "env:PLATFORM_MOMO",
                        null,
                        null,
                        "https://api.example.test/payment-providers/platform/momo/callback"));

        assertEquals(11L, response.id());
        assertEquals("****7890", response.merchantReferenceMasked());
        assertTrue(response.secretConfigured());
        assertTrue(response.ready());
        assertFalse(response.toString().contains("sandbox-secret-value"));
        assertFalse(response.toString().contains("env:PLATFORM_MOMO"));
    }

    @Test
    void productionConfigurationMutationFailsBeforePersistence() {
        when(adapterRegistry.require("VNPAY")).thenReturn(mock(PaymentProviderAdapter.class));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.configure(
                new PlatformPaymentConfigurationService.ConfigurationCommand(
                        "VNPAY",
                        PaymentEnvironment.PRODUCTION,
                        false,
                        "env:PLATFORM_VNPAY",
                        null,
                        null,
                        "https://api.example.test/payment-providers/platform/vnpay/callback")));

        assertEquals(FinancialErrorCode.PRODUCTION_NOT_APPROVED, exception.code());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void missingResolvedEndpointIsReportedAsNotReady() {
        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "ZALOPAY", PaymentEnvironment.SANDBOX);
        configuration.configure(
                true,
                "****1234",
                "env:PLATFORM_ZALOPAY",
                null,
                null,
                "https://api.example.test/payment-providers/platform/zalopay/callback");
        when(repository.findByProviderAndEnvironment("ZALOPAY", PaymentEnvironment.SANDBOX))
                .thenReturn(Optional.of(configuration));
        when(credentialResolver.resolve(configuration))
                .thenReturn(new PlatformMerchantCredentialResolver.ResolvedMerchantCredentials(
                        "merchant-1234", Map.of("secret", "configured"), null));

        PlatformPaymentConfigurationService.ConfigurationResponse response = service.get(
                "ZALOPAY", PaymentEnvironment.SANDBOX);

        assertFalse(response.ready());
        assertTrue(response.blockers().contains("provider_endpoint_missing"));
    }
}

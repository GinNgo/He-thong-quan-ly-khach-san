package com.hotel.platformbilling.config;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformPaymentConfigurationRepository
        extends JpaRepository<PlatformPaymentConfiguration, Long> {

    Optional<PlatformPaymentConfiguration> findByProviderAndEnvironment(
            String provider,
            PaymentEnvironment environment);

    List<PlatformPaymentConfiguration> findByProviderAndEnabledTrueOrderByEnvironmentAsc(String provider);

    List<PlatformPaymentConfiguration> findByEnabledTrueOrderByProviderAscEnvironmentAsc();
}

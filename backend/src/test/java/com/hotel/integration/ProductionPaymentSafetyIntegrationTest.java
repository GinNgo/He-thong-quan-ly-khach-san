package com.hotel.integration;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.ProviderCredentials;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.Readiness;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionPaymentSafetyIntegrationTest {

    private static final ProviderCredentials SANDBOX_CREDENTIALS = new ProviderCredentials(
            "sandbox-merchant",
            Map.of("secret", "sandbox-secret"),
            URI.create("https://sandbox.provider.example/pay"));

    @Test
    void productionProfileRejectsSimulatorAndSandboxInsteadOfRelabelingThemAsLive() {
        assertProductionProfileRejectsNonProductionModes("production");
    }

    @Test
    void prodProfileAliasRejectsSimulatorAndSandboxInsteadOfRelabelingThemAsLive() {
        assertProductionProfileRejectsNonProductionModes("prod");
    }

    private void assertProductionProfileRejectsNonProductionModes(String productionProfile) {
        try (ConfigurableApplicationContext context = startContext(
                productionProfile,
                "payment.demo.enabled=true",
                "payment.sandbox.enabled=true",
                "payment.production.enabled=true",
                "payment.production.approved=true")) {
            PaymentEnvironmentGuard guard = context.getBean(PaymentEnvironmentGuard.class);

            assertRejectedAsNonProductionMode(guard, productionProfile, PaymentEnvironment.SIMULATOR, null);
            assertRejectedAsNonProductionMode(
                    guard, productionProfile, PaymentEnvironment.SANDBOX, SANDBOX_CREDENTIALS);
        }
    }

    @Test
    void productionModeFailsClosedWhenRequiredConfigurationIsAbsent() {
        try (ConfigurableApplicationContext context = startContext(
                "production",
                "payment.production.enabled=false",
                "payment.production.approved=false")) {
            PaymentEnvironmentGuard guard = context.getBean(PaymentEnvironmentGuard.class);

            assertThatThrownBy(() -> guard.validate(PaymentEnvironment.PRODUCTION, "VNPAY", null))
                    .isInstanceOfSatisfying(FinancialException.class, exception -> {
                        assertThat(exception.code()).isEqualTo(FinancialErrorCode.PRODUCTION_NOT_APPROVED);
                        assertThat(exception.currentState()).isEqualTo(PaymentEnvironment.PRODUCTION.name());
                        assertThat(exception.fieldErrors()).containsKeys(
                                "production_disabled",
                                "production_not_approved",
                                "production_credentials_incomplete");
                    });
        }
    }

    @Test
    void approvedProductionModeStillRejectsASandboxEndpoint() {
        try (ConfigurableApplicationContext context = startContext(
                "production",
                "payment.production.enabled=true",
                "payment.production.approved=true")) {
            PaymentEnvironmentGuard guard = context.getBean(PaymentEnvironmentGuard.class);

            assertThatThrownBy(() -> guard.validate(
                    PaymentEnvironment.PRODUCTION,
                    "VNPAY",
                    SANDBOX_CREDENTIALS))
                    .isInstanceOfSatisfying(FinancialException.class, exception -> {
                        assertThat(exception.code()).isEqualTo(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED);
                        assertThat(exception.fieldErrors()).containsKey("production_endpoint_is_sandbox");
                    });
        }
    }

    @Test
    void nonProductionReadinessKeepsSimulatorAndSandboxLabelsExplicit() {
        try (ConfigurableApplicationContext context = startContext(
                "test",
                "payment.demo.enabled=true",
                "payment.sandbox.enabled=true",
                "payment.production.enabled=false",
                "payment.production.approved=false")) {
            PaymentEnvironmentGuard guard = context.getBean(PaymentEnvironmentGuard.class);

            Readiness simulator = guard.validate(PaymentEnvironment.SIMULATOR, "SIMULATOR", null);
            Readiness sandbox = guard.validate(PaymentEnvironment.SANDBOX, "VNPAY", SANDBOX_CREDENTIALS);

            assertThat(simulator.ready()).isTrue();
            assertThat(simulator.mode()).isEqualTo(PaymentEnvironment.SIMULATOR);
            assertThat(simulator.mode()).isNotEqualTo(PaymentEnvironment.PRODUCTION);
            assertThat(sandbox.ready()).isTrue();
            assertThat(sandbox.mode()).isEqualTo(PaymentEnvironment.SANDBOX);
            assertThat(sandbox.mode()).isNotEqualTo(PaymentEnvironment.PRODUCTION);
        }
    }

    private void assertRejectedAsNonProductionMode(
            PaymentEnvironmentGuard guard,
            String productionProfile,
            PaymentEnvironment mode,
            ProviderCredentials credentials) {
        assertThatThrownBy(() -> guard.validate(mode, "VNPAY", credentials))
                .as("profile %s must reject %s rather than expose it as production", productionProfile, mode)
                .isInstanceOfSatisfying(FinancialException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED);
                    assertThat(exception.currentState()).isEqualTo(mode.name());
                    assertThat(exception.fieldErrors()).containsKey("production_profile_requires_production_mode");
                });
    }

    private ConfigurableApplicationContext startContext(String profile, String... properties) {
        String[] arguments = new String[properties.length];
        for (int index = 0; index < properties.length; index++) {
            arguments[index] = "--" + properties[index];
        }
        return new SpringApplicationBuilder(GuardTestConfiguration.class)
                .profiles(profile)
                .bannerMode(Banner.Mode.OFF)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run(arguments);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(PaymentEnvironmentGuard.class)
    static class GuardTestConfiguration {
    }
}

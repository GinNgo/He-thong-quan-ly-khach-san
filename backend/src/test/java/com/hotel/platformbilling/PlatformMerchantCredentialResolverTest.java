package com.hotel.platformbilling;

import com.hotel.platformbilling.config.PlatformMerchantCredentialResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformMerchantCredentialResolverTest {

    @Test
    void resolvesInternalPlatformSimulatorFromExistingDemoSecret() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("payment.demo.signing-secret", "test-signing-secret");
        PlatformMerchantCredentialResolver resolver = new PlatformMerchantCredentialResolver(environment);

        PlatformMerchantCredentialResolver.ResolvedMerchantCredentials credentials =
                resolver.resolveReference("internal://simulator");

        assertEquals("PLATFORM-SIMULATOR", credentials.merchantId());
        assertEquals("test-signing-secret", credentials.secrets().get("signingSecret"));
    }
}

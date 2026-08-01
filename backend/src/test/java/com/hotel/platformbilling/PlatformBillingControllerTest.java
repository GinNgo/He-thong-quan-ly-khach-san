package com.hotel.platformbilling;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.platformbilling.config.PlatformMerchantCredentialResolver;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.SubscriptionOrderService;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptService;
import com.hotel.platformbilling.subscription.SubscriptionPolicyService;
import com.hotel.platformbilling.subscription.SubscriptionRenewalService;
import com.hotel.platformbilling.subscription.SubscriptionUpgradeService;
import com.hotel.services.SubscriptionCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformBillingControllerTest {

    @Mock private SubscriptionCatalogService catalogService;
    @Mock private SubscriptionOrderService orderService;
    @Mock private PlatformPaymentAttemptService attemptService;
    @Mock private SubscriptionRenewalService renewalService;
    @Mock private SubscriptionUpgradeService upgradeService;
    @Mock private SubscriptionPolicyService policyService;
    @Mock private PlatformPaymentConfigurationService configurationService;
    @Mock private PlatformBillingQueryService queryService;

    private PlatformBillingController controller;

    @BeforeEach
    void setUp() {
        controller = new PlatformBillingController(
                catalogService, orderService, attemptService, renewalService, upgradeService,
                policyService, configurationService, queryService);
    }

    @Test
    void purchaseEndpointDelegatesOnlyIdentifiersAndTheIdempotencyHeader() {
        SubscriptionOrderService.OrderResponse expected = mock(SubscriptionOrderService.OrderResponse.class);
        when(orderService.createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(20L, 30L, "purchase-key")))
                .thenReturn(expected);

        var response = controller.createPurchaseOrder(
                new PlatformBillingController.PurchaseOrderRequest(20L, 30L), "purchase-key");

        assertEquals(expected, response.getBody());
        verify(orderService).createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(20L, 30L, "purchase-key"));
    }

    @Test
    void readinessEndpointReturnsOnlyTheMaskedReadinessProjection() {
        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "MOMO", PaymentEnvironmentGuard.PaymentEnvironment.SANDBOX);
        PlatformMerchantCredentialResolver.ResolvedMerchantCredentials credentials =
                new PlatformMerchantCredentialResolver.ResolvedMerchantCredentials(
                        "merchant-secret-id", Map.of("secretKey", "must-not-leak"),
                        URI.create("https://sandbox.example.test"));
        PaymentEnvironmentGuard.Readiness readiness = new PaymentEnvironmentGuard.Readiness(
                true, PaymentEnvironmentGuard.PaymentEnvironment.SANDBOX,
                "MOMO", "****t-id", List.of());
        when(configurationService.requireReady("MOMO"))
                .thenReturn(new PlatformPaymentConfigurationService.ReadyConfiguration(
                        configuration, credentials, readiness));

        var response = controller.validateConfiguration("MOMO");

        assertEquals(readiness, response.getBody());
    }
}

package com.hotel.platformbilling;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptRepository;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptService;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformPaymentAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T05:00:00Z");

    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformPaymentAttemptRepository attemptRepository;
    @Mock private PlatformPaymentConfigurationService configurationService;
    @Mock private PropertyAccessService propertyAccessService;

    private PlatformPaymentAttemptService service;

    @BeforeEach
    void setUp() {
        service = new PlatformPaymentAttemptService(
                orderRepository,
                attemptRepository,
                configurationService,
                propertyAccessService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAttemptOnlyFromOrderPriceAndSystemMerchantConfiguration() {
        Fixture fixture = fixture(NOW.plusSeconds(1800));
        authorize(fixture);
        when(orderRepository.findByPublicIdForUpdate("order-public"))
                .thenReturn(Optional.of(fixture.order()));
        when(attemptRepository.findByOrderIdAndIdempotencyKey(40L, "attempt-key"))
                .thenReturn(Optional.empty());
        when(attemptRepository.findByOrderIdOrderByCreatedAtAsc(40L)).thenReturn(List.of());
        when(configurationService.requireReady("SIMULATOR")).thenReturn(ready(fixture.configuration()));
        when(orderRepository.save(any(SubscriptionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attemptRepository.saveAndFlush(any(PlatformPaymentAttempt.class))).thenAnswer(invocation -> {
            PlatformPaymentAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "id", 50L);
            return attempt;
        });

        PlatformPaymentAttemptService.AttemptResponse response = service.create(
                new PlatformPaymentAttemptService.CreateAttemptCommand(
                        "order-public", "SIMULATOR", "MOMO", "attempt-key"));

        assertEquals(50L, response.id());
        assertEquals(VndMoney.of(2_400_000).amount(), response.expectedAmount());
        assertEquals(PlatformPaymentAttempt.Status.PENDING, response.status());
        assertEquals(SubscriptionOrderState.PENDING_PAYMENT, fixture.order().getStatus());
        assertEquals(fixture.order().getExpiresAt(), response.expiresAt());
        assertFalse(response.replayed());

        ArgumentCaptor<PlatformPaymentAttempt> captor = ArgumentCaptor.forClass(PlatformPaymentAttempt.class);
        verify(attemptRepository).saveAndFlush(captor.capture());
        assertTrue(captor.getValue().getConfiguration() instanceof PlatformPaymentConfiguration);
        assertEquals(fixture.order().getPrice(), captor.getValue().getExpectedAmount());
    }

    @Test
    void equivalentAttemptReplayDoesNotResolveAnotherMerchant() {
        Fixture fixture = fixture(NOW.plusSeconds(1800));
        PlatformPaymentAttempt existing = PlatformPaymentAttempt.create(
                "attempt-public",
                fixture.order(),
                fixture.configuration(),
                "MOMO",
                fixture.order().priceMoney(),
                "attempt-key",
                sha256("order-public|SIMULATOR|MOMO"),
                fixture.order().getExpiresAt());
        existing.markPending("attempt-public");
        authorize(fixture);
        when(orderRepository.findByPublicIdForUpdate("order-public"))
                .thenReturn(Optional.of(fixture.order()));
        when(attemptRepository.findByOrderIdAndIdempotencyKey(40L, "attempt-key"))
                .thenReturn(Optional.of(existing));

        PlatformPaymentAttemptService.AttemptResponse response = service.create(
                new PlatformPaymentAttemptService.CreateAttemptCommand(
                        "order-public", "SIMULATOR", "MOMO", "attempt-key"));

        assertEquals("attempt-public", response.publicId());
        assertTrue(response.replayed());
        verify(configurationService, never()).requireReady(any());
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void expiredOrderIsClosedBeforeMerchantOrAttemptMutation() {
        Fixture fixture = fixture(NOW.minusSeconds(1));
        authorize(fixture);
        when(orderRepository.findByPublicIdForUpdate("order-public"))
                .thenReturn(Optional.of(fixture.order()));
        when(orderRepository.saveAndFlush(fixture.order())).thenReturn(fixture.order());

        FinancialException exception = assertThrows(FinancialException.class, () -> service.create(
                new PlatformPaymentAttemptService.CreateAttemptCommand(
                        "order-public", "SIMULATOR", "MOMO", "attempt-key")));

        assertEquals(FinancialErrorCode.ATTEMPT_EXPIRED, exception.code());
        assertEquals(SubscriptionOrderState.EXPIRED, fixture.order().getStatus());
        verify(configurationService, never()).requireReady(any());
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    private void authorize(Fixture fixture) {
        when(propertyAccessService.currentUser()).thenReturn(fixture.owner());
    }

    private PlatformPaymentConfigurationService.ReadyConfiguration ready(
            PlatformPaymentConfiguration configuration) {
        return new PlatformPaymentConfigurationService.ReadyConfiguration(
                configuration,
                null,
                new PaymentEnvironmentGuard.Readiness(
                        true,
                        PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR,
                        "SIMULATOR",
                        null,
                        List.of()));
    }

    private Fixture fixture(Instant expiresAt) {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");

        Hotel hotel = new Hotel();
        hotel.setId(20L);
        hotel.setName("Platform Fixture Hotel");

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(30L);
        plan.setCode("PRO");
        plan.setNameVi("Professional");
        plan.setBillingType("YEARLY");
        plan.setPrice(VndMoney.of(2_400_000).amount());
        plan.setStatus("ACTIVE");

        SubscriptionOrder order = SubscriptionOrder.create(
                "order-public",
                "SUB-20260801-ORDER",
                owner,
                hotel,
                SubscriptionOrder.Operation.PURCHASE,
                plan,
                "PLAN-30-V1",
                "PRO",
                "Professional",
                VndMoney.of(2_400_000),
                "YEARLY",
                1,
                SubscriptionOrder.DurationUnit.YEAR,
                "{\"features\":[]}",
                "order-key",
                "order-hash",
                LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        ReflectionTestUtils.setField(order, "id", 40L);

        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "SIMULATOR", PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR);
        configuration.configure(true, null, null, null, null, null);
        ReflectionTestUtils.setField(configuration, "id", 41L);
        return new Fixture(owner, order, configuration);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(
            User owner,
            SubscriptionOrder order,
            PlatformPaymentConfiguration configuration) {
    }
}

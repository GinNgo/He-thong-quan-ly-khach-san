package com.hotel.platformbilling;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.hotel.platformbilling.order.PlatformSubscriptionPlanCatalogRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.order.SubscriptionOrderService;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptRepository;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptService;
import com.hotel.repositories.UserRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies tenant ownership and the boundary between property and platform merchants. */
@ExtendWith(MockitoExtension.class)
class PlatformBillingSecurityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-01T06:00:00Z");

    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformSubscriptionPlanCatalogRepository planRepository;
    @Mock private PlatformPaymentAttemptRepository attemptRepository;
    @Mock private UserRepository userRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private PlatformPaymentConfigurationService configurationService;

    private SubscriptionOrderService orderService;
    private PlatformPaymentAttemptService attemptService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        orderService = new SubscriptionOrderService(
                planRepository, orderRepository, userRepository, propertyAccessService,
                new ObjectMapper(), clock, 30);
        attemptService = new PlatformPaymentAttemptService(
                orderRepository, attemptRepository, configurationService, propertyAccessService, clock);
    }

    @Test
    void orderOwnerMismatchReturnsNotFoundBeforeMerchantOrAttemptMutation() {
        Fixture fixture = fixture();
        User differentOwner = user(11L, "different-owner");
        when(orderRepository.findByPublicIdForUpdate("order-public"))
                .thenReturn(Optional.of(fixture.order()));
        when(propertyAccessService.currentUser()).thenReturn(differentOwner);

        FinancialException exception = assertThrows(FinancialException.class, () -> attemptService.create(
                new PlatformPaymentAttemptService.CreateAttemptCommand(
                        "order-public", "MOMO", "MOMO", "attempt-key")));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
        verify(configurationService, never()).requireReady(any());
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void unmanagedPropertyIsDeniedBeforeCatalogSnapshotOrOrderPersistence() {
        User owner = user(10L, "owner");
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(propertyAccessService.requireAssignedHotel(20L))
                .thenThrow(new SecurityException("property is not managed by the owner"));

        assertThrows(SecurityException.class, () -> orderService.createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(20L, 30L, "purchase-key")));

        verify(planRepository, never()).findByIdForSnapshot(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void platformAttemptBindsOnlyTheSystemPlatformMerchantConfiguration() {
        Fixture fixture = fixture();
        PlatformPaymentConfiguration platformConfiguration = PlatformPaymentConfiguration.create(
                "MOMO", PaymentEnvironmentGuard.PaymentEnvironment.SANDBOX);
        platformConfiguration.configure(
                true, "****PLAT", "env:PLATFORM_MOMO", null, null,
                "https://api.example.test/platform/momo/callback");
        when(orderRepository.findByPublicIdForUpdate("order-public"))
                .thenReturn(Optional.of(fixture.order()));
        when(propertyAccessService.currentUser()).thenReturn(fixture.owner());
        when(attemptRepository.findByOrderIdAndIdempotencyKey(40L, "attempt-key"))
                .thenReturn(Optional.empty());
        when(attemptRepository.findByOrderIdOrderByCreatedAtAsc(40L)).thenReturn(List.of());
        when(configurationService.requireReady("MOMO")).thenReturn(new PlatformPaymentConfigurationService.ReadyConfiguration(
                platformConfiguration,
                null,
                new PaymentEnvironmentGuard.Readiness(
                        true, PaymentEnvironmentGuard.PaymentEnvironment.SANDBOX,
                        "MOMO", "****PLAT", List.of())));
        when(attemptRepository.saveAndFlush(any(PlatformPaymentAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlatformPaymentAttemptService.AttemptResponse response = attemptService.create(
                new PlatformPaymentAttemptService.CreateAttemptCommand(
                        "order-public", "MOMO", "MOMO", "attempt-key"));

        assertEquals("****PLAT", response.merchantReferenceMasked());
        ArgumentCaptor<PlatformPaymentAttempt> captor = ArgumentCaptor.forClass(PlatformPaymentAttempt.class);
        verify(attemptRepository).saveAndFlush(captor.capture());
        assertSame(platformConfiguration, captor.getValue().getConfiguration());
        verify(configurationService).requireReady("MOMO");
    }

    private Fixture fixture() {
        User owner = user(10L, "owner");
        Hotel hotel = new Hotel();
        ReflectionTestUtils.setField(hotel, "id", 20L);
        hotel.setName("Platform Fixture Hotel");

        SubscriptionPlan plan = new SubscriptionPlan();
        ReflectionTestUtils.setField(plan, "id", 30L);
        plan.setCode("PRO");
        plan.setNameVi("Professional");
        plan.setBillingType("YEARLY");
        plan.setPrice(BigDecimal.valueOf(2_400_000));
        plan.setStatus("ACTIVE");

        SubscriptionOrder order = SubscriptionOrder.create(
                "order-public", "SUB-20260801-ORDER", owner, hotel,
                SubscriptionOrder.Operation.PURCHASE, plan, "PLAN-30-V1", "PRO", "Professional",
                VndMoney.of(2_400_000), "YEARLY", 1, SubscriptionOrder.DurationUnit.YEAR,
                "{\"features\":[]}", "order-key", "order-hash",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(30));
        ReflectionTestUtils.setField(order, "id", 40L);
        order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return new Fixture(owner, order);
    }

    private User user(Long id, String username) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setStatus("ACTIVE");
        return user;
    }

    private record Fixture(User owner, SubscriptionOrder order) {
    }
}

package com.hotel.platformbilling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.PlatformSubscriptionPlanCatalogRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.order.SubscriptionOrderService;
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
import java.util.LinkedHashSet;
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
class SubscriptionOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T04:00:00Z");

    @Mock private PlatformSubscriptionPlanCatalogRepository planRepository;
    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private PropertyAccessService propertyAccessService;

    private SubscriptionOrderService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionOrderService(
                planRepository,
                orderRepository,
                userRepository,
                propertyAccessService,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                30);
    }

    @Test
    void createsAnExpiringOrderFromTheLockedBackendCatalogSnapshot() {
        Fixture fixture = fixture();
        authorize(fixture);
        when(orderRepository.findByOwnerIdAndIdempotencyKeyForUpdate(10L, "purchase-key"))
                .thenReturn(Optional.empty());
        when(planRepository.findByIdForSnapshot(30L)).thenReturn(Optional.of(fixture.plan()));
        when(orderRepository.saveAndFlush(any(SubscriptionOrder.class))).thenAnswer(invocation -> {
            SubscriptionOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 40L);
            return order;
        });

        SubscriptionOrderService.OrderResponse response = service.createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(20L, 30L, "purchase-key"));

        assertEquals(40L, response.id());
        assertEquals(SubscriptionOrderState.CREATED, response.status());
        assertEquals(BigDecimal.valueOf(2_400_000).setScale(0), response.price());
        assertEquals(SubscriptionOrder.DurationUnit.YEAR, response.durationUnit());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(30), response.expiresAt());
        assertTrue(response.planVersion().startsWith("PLAN-30-"));
        assertTrue(response.featureSnapshotJson().contains("REPORT_EXPORT"));
        assertTrue(response.featureSnapshotJson().contains("MAX_ROOMS"));
        assertFalse(response.replayed());

        ArgumentCaptor<SubscriptionOrder> captor = ArgumentCaptor.forClass(SubscriptionOrder.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertEquals("PRO", captor.getValue().getPlanCode());
        assertEquals("Professional", captor.getValue().getPlanName());
    }

    @Test
    void equivalentReplayReturnsTheOriginalSnapshotWithoutReadingTheCurrentCatalog() {
        Fixture fixture = fixture();
        authorize(fixture);
        when(orderRepository.findByOwnerIdAndIdempotencyKeyForUpdate(10L, "purchase-key"))
                .thenReturn(Optional.empty());
        when(planRepository.findByIdForSnapshot(30L)).thenReturn(Optional.of(fixture.plan()));
        when(orderRepository.saveAndFlush(any(SubscriptionOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionOrderService.OrderResponse created = service.createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(20L, 30L, "purchase-key"));
        ArgumentCaptor<SubscriptionOrder> captor = ArgumentCaptor.forClass(SubscriptionOrder.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        SubscriptionOrder existing = captor.getValue();

        org.mockito.Mockito.reset(planRepository, orderRepository);
        when(orderRepository.findByOwnerIdAndIdempotencyKeyForUpdate(10L, "purchase-key"))
                .thenReturn(Optional.of(existing));

        SubscriptionOrderService.OrderResponse replay = service.createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(20L, 30L, "purchase-key"));

        assertEquals(created.publicId(), replay.publicId());
        assertEquals(created.featureSnapshotJson(), replay.featureSnapshotJson());
        assertTrue(replay.replayed());
        verify(planRepository, never()).findByIdForSnapshot(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void renewalOrderUsesTheCurrentMonthlyCatalogDurationInsteadOfOneHardCodedYear() {
        Fixture fixture = fixture();
        fixture.plan().setBillingType("MONTHLY");
        fixture.plan().setPrice(BigDecimal.valueOf(250_000));
        authorize(fixture);
        when(orderRepository.findByOwnerIdAndIdempotencyKeyForUpdate(10L, "renew-key"))
                .thenReturn(Optional.empty());
        when(planRepository.findByIdForSnapshot(30L)).thenReturn(Optional.of(fixture.plan()));
        when(orderRepository.saveAndFlush(any(SubscriptionOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionOrderService.OrderResponse response = service.createLifecycleOrder(
                new SubscriptionOrderService.CreateLifecycleOrderCommand(
                        20L,
                        30L,
                        SubscriptionOrder.Operation.RENEW,
                        "renew-key"));

        assertEquals(SubscriptionOrder.Operation.RENEW, response.operation());
        assertEquals(BigDecimal.valueOf(250_000).setScale(0), response.price());
        assertEquals("MONTHLY", response.billingPeriod());
        assertEquals(1, response.durationValue());
        assertEquals(SubscriptionOrder.DurationUnit.MONTH, response.durationUnit());
    }

    @Test
    void renewalOrderRejectsCatalogTermsThatWouldConvertToLifetimeAccess() {
        Fixture fixture = fixture();
        fixture.plan().setIsLifetime(true);
        authorize(fixture);
        when(orderRepository.findByOwnerIdAndIdempotencyKeyForUpdate(10L, "renew-key"))
                .thenReturn(Optional.empty());
        when(planRepository.findByIdForSnapshot(30L)).thenReturn(Optional.of(fixture.plan()));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.createLifecycleOrder(
                new SubscriptionOrderService.CreateLifecycleOrderCommand(
                        20L,
                        30L,
                        SubscriptionOrder.Operation.RENEW,
                        "renew-key")));

        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, exception.code());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void reusedKeyWithDifferentTargetIsRejectedBeforeCatalogOrPersistence() {
        Fixture fixture = fixture();
        authorize(fixture);
        when(orderRepository.findByOwnerIdAndIdempotencyKeyForUpdate(10L, "purchase-key"))
                .thenReturn(Optional.empty());
        when(planRepository.findByIdForSnapshot(30L)).thenReturn(Optional.of(fixture.plan()));
        when(orderRepository.saveAndFlush(any(SubscriptionOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(20L, 30L, "purchase-key"));
        ArgumentCaptor<SubscriptionOrder> captor = ArgumentCaptor.forClass(SubscriptionOrder.class);
        verify(orderRepository).saveAndFlush(captor.capture());

        Hotel secondHotel = new Hotel();
        secondHotel.setId(21L);
        when(propertyAccessService.requireManagedHotel(21L)).thenReturn(secondHotel);
        org.mockito.Mockito.reset(planRepository, orderRepository);
        when(orderRepository.findByOwnerIdAndIdempotencyKeyForUpdate(10L, "purchase-key"))
                .thenReturn(Optional.of(captor.getValue()));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(21L, 30L, "purchase-key")));

        assertEquals(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED, exception.code());
        verify(planRepository, never()).findByIdForSnapshot(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    private void authorize(Fixture fixture) {
        when(propertyAccessService.currentUser()).thenReturn(fixture.owner());
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fixture.owner()));
        when(propertyAccessService.requireManagedHotel(20L)).thenReturn(fixture.hotel());
    }

    private Fixture fixture() {
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
        plan.setNameEn("Professional");
        plan.setBillingType("YEARLY");
        plan.setPrice(BigDecimal.valueOf(2_400_000));
        plan.setIsLifetime(false);
        plan.setStatus("ACTIVE");
        plan.setFeatures(new LinkedHashSet<>());
        plan.getFeatures().add(feature(plan, "MAX_ROOMS", 50));
        plan.getFeatures().add(feature(plan, "REPORT_EXPORT", 1));
        return new Fixture(owner, hotel, plan);
    }

    private PlanFeature feature(SubscriptionPlan plan, String code, int limit) {
        PlanFeature feature = new PlanFeature();
        feature.setPlan(plan);
        feature.setFeatureCode(code);
        feature.setLimitValue(limit);
        return feature;
    }

    private record Fixture(User owner, Hotel hotel, SubscriptionPlan plan) {
    }
}

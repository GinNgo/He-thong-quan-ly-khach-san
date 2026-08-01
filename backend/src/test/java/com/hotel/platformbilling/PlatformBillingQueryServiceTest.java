package com.hotel.platformbilling;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptRepository;
import com.hotel.platformbilling.subscription.PlatformSubscriptionHistoryRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformBillingQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformPaymentAttemptRepository attemptRepository;
    @Mock private PlatformSubscriptionHistoryRepository historyRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private FinancialAuditService auditService;

    @Test
    void cancellationClosesTheUnpaidOrderAndItsPendingAttemptTogether() {
        Fixture fixture = fixture();
        PlatformBillingQueryService service = new PlatformBillingQueryService(
                orderRepository, attemptRepository, historyRepository, propertyAccessService,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(orderRepository.findByPublicIdForUpdate("order-public"))
                .thenReturn(Optional.of(fixture.order()));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(fixture.owner());
        when(attemptRepository.findByOrderIdOrderByCreatedAtAsc(40L))
                .thenReturn(List.of(fixture.attempt()));
        when(orderRepository.saveAndFlush(fixture.order())).thenReturn(fixture.order());

        PlatformBillingQueryService.OrderDetails result = service.cancelOrder("order-public", "cancel-correlation");

        assertEquals(SubscriptionOrderState.CANCELLED, result.status());
        assertEquals(PlatformPaymentAttempt.Status.CANCELLED, result.attempts().get(0).status());
        verify(attemptRepository).saveAll(List.of(fixture.attempt()));
        verify(orderRepository).saveAndFlush(fixture.order());
        verify(auditService).append(org.mockito.ArgumentMatchers.any(FinancialAuditService.AuditCommand.class));
    }

    private Fixture fixture() {
        User owner = new User();
        owner.setId(10L);
        Hotel hotel = new Hotel();
        hotel.setId(20L);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(30L);
        plan.setCode("PRO");
        plan.setNameVi("Professional");
        plan.setBillingType("YEARLY");
        plan.setPrice(VndMoney.of(2_400_000).amount());
        plan.setStatus("ACTIVE");
        SubscriptionOrder order = SubscriptionOrder.create(
                "order-public", "SUB-20260801-QUERY", owner, hotel,
                SubscriptionOrder.Operation.PURCHASE, plan, "PLAN-30-V1", "PRO", "Professional",
                VndMoney.of(2_400_000), "YEARLY", 1, SubscriptionOrder.DurationUnit.YEAR,
                "{\"features\":[]}", "order-key", "order-hash",
                LocalDateTime.ofInstant(NOW.plusSeconds(1800), ZoneOffset.UTC));
        order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT,
                LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        ReflectionTestUtils.setField(order, "id", 40L);
        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "SIMULATOR", PaymentEnvironment.SIMULATOR);
        configuration.configure(true, "****FORM", "env:PLATFORM_SIMULATOR", null, null, null);
        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                "attempt-public", order, configuration, "MOMO", order.priceMoney(),
                "attempt-key", "attempt-hash", order.getExpiresAt());
        attempt.markPending("attempt-public");
        ReflectionTestUtils.setField(attempt, "id", 50L);
        return new Fixture(owner, order, attempt);
    }

    private record Fixture(User owner, SubscriptionOrder order, PlatformPaymentAttempt attempt) {
    }
}

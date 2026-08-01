package com.hotel.platformbilling;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import com.hotel.platformbilling.config.PlatformMerchantCredentialResolver;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptRepository;
import com.hotel.platformbilling.payment.PlatformPaymentCallbackService;
import com.hotel.platformbilling.subscription.SubscriptionApplicationService;
import com.hotel.platformbilling.subscription.SubscriptionEntitlement;
import com.hotel.platformbilling.subscription.SubscriptionHistory;
import com.hotel.platformbilling.subscription.SubscriptionRenewalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformPaymentCallbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T06:00:00Z");

    @Mock private PlatformPaymentAttemptRepository attemptRepository;
    @Mock private PlatformFinancialTransactionRepository transactionRepository;
    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformPaymentConfigurationService configurationService;
    @Mock private PaymentProviderAdapterRegistry adapterRegistry;
    @Mock private PaymentProviderAdapter adapter;
    @Mock private FinancialAuditService auditService;
    @Mock private SubscriptionApplicationService applicationService;
    @Mock private SubscriptionRenewalService renewalService;

    private PlatformPaymentCallbackService service;

    @BeforeEach
    void setUp() {
        service = new PlatformPaymentCallbackService(
                attemptRepository,
                transactionRepository,
                orderRepository,
                configurationService,
                adapterRegistry,
                auditService,
                applicationService,
                renewalService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void verifiedSuccessCreatesOnePlatformLedgerEffectAndEquivalentReplayReturnsIt() {
        Fixture fixture = fixture();
        PaymentProviderAdapter.NormalizedCallback callback = callback(true, "event-1", "txn-1");
        arrange(fixture, callback);
        arrangePurchaseApplication(fixture);
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(50L)).thenReturn(List.of());
        when(transactionRepository.findByIdempotencyIdentity(any())).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(PlatformFinancialTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlatformPaymentCallbackService.CallbackResult first = service.process(command());

        assertTrue(first.accepted());
        assertFalse(first.replayed());
        assertEquals(PlatformPaymentAttempt.Status.SUCCESS, first.attemptStatus());
        assertEquals(SubscriptionOrderState.APPLIED, first.orderStatus());
        assertEquals("contract-public", first.contractPublicId());
        ArgumentCaptor<PlatformFinancialTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PlatformFinancialTransaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
        PlatformFinancialTransaction transaction = transactionCaptor.getValue();
        assertEquals(PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                transaction.getTransactionType());
        assertEquals(fixture.attempt().getExpectedAmount(), transaction.getAmount());
        assertEquals(fixture.attempt(), transaction.getAttempt());

        when(attemptRepository.findByProviderEventForUpdate(
                "SIMULATOR", PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR, "event-1"))
                .thenReturn(Optional.of(fixture.attempt()));
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(50L))
                .thenReturn(List.of(transaction));

        PlatformPaymentCallbackService.CallbackResult replay = service.process(command());

        assertTrue(replay.accepted());
        assertTrue(replay.replayed());
        assertEquals(SubscriptionOrderState.APPLIED, replay.orderStatus());
        assertEquals(transaction.getPublicId(), replay.transactionPublicId());
        assertEquals("contract-public", replay.contractPublicId());
        verify(transactionRepository, times(1)).saveAndFlush(any(PlatformFinancialTransaction.class));
        verify(applicationService, times(2)).applyPaidOrder(
                "order-public", transaction.getPublicId(), "correlation-platform-callback");
    }

    @Test
    void rejectedBindingChangesNoAttemptOrderOrLedgerState() {
        Fixture fixture = fixture();
        PaymentProviderAdapter.NormalizedCallback callback = callback(true, "event-2", "txn-2");
        arrange(fixture, callback);
        when(adapter.verify(any())).thenReturn(
                PaymentProviderAdapter.VerificationResult.rejectedResult(
                        FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH));

        PlatformPaymentCallbackService.CallbackResult result = service.process(command());

        assertFalse(result.accepted());
        assertEquals(FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH, result.errorCode());
        assertEquals(PlatformPaymentAttempt.Status.PENDING, fixture.attempt().getStatus());
        assertEquals(SubscriptionOrderState.PENDING_PAYMENT, fixture.order().getStatus());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void verifiedFailureClosesOrderWithoutCreatingPlatformRevenue() {
        Fixture fixture = fixture();
        PaymentProviderAdapter.NormalizedCallback callback = new PaymentProviderAdapter.NormalizedCallback(
                "SIMULATOR",
                "event-failed",
                null,
                "attempt-public",
                BigDecimal.valueOf(2_400_000),
                "VND",
                NOW,
                Map.of("successful", false, "status", "FAILED"));
        arrange(fixture, callback);
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(50L)).thenReturn(List.of());

        PlatformPaymentCallbackService.CallbackResult result = service.process(command());

        assertTrue(result.accepted());
        assertFalse(result.replayed());
        assertEquals(PlatformPaymentAttempt.Status.FAILED, result.attemptStatus());
        assertEquals(SubscriptionOrderState.FAILED, result.orderStatus());
        assertNull(result.transactionPublicId());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(attemptRepository).saveAndFlush(fixture.attempt());
        verify(orderRepository).save(fixture.order());
    }

    @Test
    void verifiedRenewalDelegatesThePaidOrderToTheRenewalApplicationBoundary() {
        Fixture fixture = fixture(SubscriptionOrder.Operation.RENEW);
        PaymentProviderAdapter.NormalizedCallback callback = callback(true, "event-renew", "txn-renew");
        arrange(fixture, callback);
        arrangeRenewalApplication(fixture);
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(50L)).thenReturn(List.of());
        when(transactionRepository.findByIdempotencyIdentity(any())).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(PlatformFinancialTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlatformPaymentCallbackService.CallbackResult result = service.process(command());

        assertTrue(result.accepted());
        assertEquals(SubscriptionOrderState.APPLIED, result.orderStatus());
        assertEquals("renewed-contract", result.contractPublicId());
        ArgumentCaptor<PlatformFinancialTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PlatformFinancialTransaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
        assertEquals(
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_RENEWAL,
                transactionCaptor.getValue().getTransactionType());
        verify(renewalService).applyPaidRenewal(
                "order-public", transactionCaptor.getValue().getPublicId(), "correlation-platform-callback");
        verify(applicationService, never()).applyPaidOrder(any(), any(), any());
    }

    private void arrange(Fixture fixture, PaymentProviderAdapter.NormalizedCallback callback) {
        when(adapterRegistry.require("SIMULATOR")).thenReturn(adapter);
        when(adapter.normalize(any())).thenReturn(callback);
        when(adapter.verify(any())).thenReturn(PaymentProviderAdapter.VerificationResult.acceptedResult());
        when(attemptRepository.findByProviderAndReferenceForUpdate("SIMULATOR", "attempt-public"))
                .thenReturn(Optional.of(fixture.attempt()));
        when(orderRepository.findByPublicIdForUpdate("order-public"))
                .thenReturn(Optional.of(fixture.order()));
        when(configurationService.requireReady("SIMULATOR"))
                .thenReturn(ready(fixture.configuration()));
        org.mockito.Mockito.lenient().when(attemptRepository.findByProviderEventForUpdate(
                "SIMULATOR", PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR, callback.eventId()))
                .thenReturn(Optional.empty());
    }

    private void arrangePurchaseApplication(Fixture fixture) {
        when(applicationService.applyPaidOrder(
                org.mockito.ArgumentMatchers.eq("order-public"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("correlation-platform-callback")))
                .thenAnswer(invocation -> {
                    boolean replayed = fixture.order().getStatus() == SubscriptionOrderState.APPLIED;
                    if (fixture.order().getStatus() == SubscriptionOrderState.PAID) {
                        fixture.order().transitionTo(
                                SubscriptionOrderState.APPLIED,
                                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
                    }
                    return new SubscriptionApplicationService.ApplicationResult(
                            fixture.order().getPublicId(),
                            fixture.order().getStatus(),
                            invocation.getArgument(1),
                            "contract-public",
                            fixture.order().getTargetHotel().getId(),
                            SubscriptionEntitlement.Status.ACTIVE,
                            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusYears(1),
                            false,
                            SubscriptionHistory.ActionType.PURCHASED,
                            replayed);
                });
    }

    private void arrangeRenewalApplication(Fixture fixture) {
        when(renewalService.applyPaidRenewal(
                org.mockito.ArgumentMatchers.eq("order-public"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("correlation-platform-callback")))
                .thenAnswer(invocation -> {
                    fixture.order().transitionTo(
                            SubscriptionOrderState.APPLIED,
                            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
                    return new SubscriptionRenewalService.RenewalApplicationResult(
                            fixture.order().getPublicId(),
                            fixture.order().getStatus(),
                            invocation.getArgument(1),
                            "renewed-contract",
                            fixture.order().getTargetHotel().getId(),
                            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMonths(1),
                            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMonths(1),
                            SubscriptionHistory.ActionType.RENEWED,
                            false);
                });
    }

    private PlatformPaymentConfigurationService.ReadyConfiguration ready(
            PlatformPaymentConfiguration configuration) {
        PlatformMerchantCredentialResolver.ResolvedMerchantCredentials credentials =
                new PlatformMerchantCredentialResolver.ResolvedMerchantCredentials(
                        "SIM-PLATFORM",
                        Map.of("signingSecret", "test-signing-secret"),
                        URI.create("https://simulator.example.test"));
        return new PlatformPaymentConfigurationService.ReadyConfiguration(
                configuration,
                credentials,
                new PaymentEnvironmentGuard.Readiness(
                        true,
                        PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR,
                        "SIMULATOR",
                        "****FORM",
                        List.of()));
    }

    private PaymentProviderAdapter.NormalizedCallback callback(
            boolean successful,
            String eventId,
            String transactionId) {
        return new PaymentProviderAdapter.NormalizedCallback(
                "SIMULATOR",
                eventId,
                transactionId,
                "attempt-public",
                BigDecimal.valueOf(2_400_000),
                "VND",
                NOW,
                Map.of("successful", successful, "status", successful ? "SUCCESS" : "FAILED"));
    }

    private PlatformPaymentCallbackService.CallbackCommand command() {
        return new PlatformPaymentCallbackService.CallbackCommand(
                "SIMULATOR",
                "signed-callback",
                Map.of("reference", "attempt-public"),
                NOW,
                "correlation-platform-callback");
    }

    private Fixture fixture() {
        return fixture(SubscriptionOrder.Operation.PURCHASE);
    }

    private Fixture fixture(SubscriptionOrder.Operation operation) {
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
                "order-public",
                "SUB-20260801-CALLBACK",
                owner,
                hotel,
                operation,
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
                LocalDateTime.ofInstant(NOW.plusSeconds(1800), ZoneOffset.UTC));
        order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT,
                LocalDateTime.ofInstant(NOW.minusSeconds(120), ZoneOffset.UTC));
        ReflectionTestUtils.setField(order, "id", 40L);

        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "SIMULATOR", PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR);
        configuration.configure(true, "****FORM", "env:PLATFORM_SIMULATOR", null, null, null);
        ReflectionTestUtils.setField(configuration, "id", 41L);

        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                "attempt-public",
                order,
                configuration,
                "MOMO",
                order.priceMoney(),
                "attempt-key",
                "attempt-hash",
                order.getExpiresAt());
        attempt.markPending("attempt-public");
        ReflectionTestUtils.setField(attempt, "id", 50L);
        return new Fixture(order, configuration, attempt);
    }

    private record Fixture(
            SubscriptionOrder order,
            PlatformPaymentConfiguration configuration,
            PlatformPaymentAttempt attempt) {
    }
}

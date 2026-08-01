package com.hotel.platformbilling.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionApplicationServiceTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 8, 1, 6, 0);

    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformFinancialTransactionRepository transactionRepository;
    @Mock private PlatformSoftwareContractRepository contractRepository;
    @Mock private SubscriptionEntitlementRepository entitlementRepository;
    @Mock private PlatformSubscriptionHistoryRepository historyRepository;
    @Mock private FinancialAuditService auditService;

    private SubscriptionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionApplicationService(
                orderRepository,
                transactionRepository,
                contractRepository,
                entitlementRepository,
                historyRepository,
                auditService,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void paidPurchaseCreatesOneContractEntitlementAndHistoryThenMarksOrderApplied() {
        Fixture fixture = fixture("order-public");
        arrangeNewApplication(fixture);

        SubscriptionApplicationService.ApplicationResult result = service.applyPaidOrder(
                fixture.order().getPublicId(), fixture.transaction().getPublicId(), "apply-correlation");

        assertEquals(SubscriptionOrderState.APPLIED, result.orderStatus());
        assertEquals(SubscriptionEntitlement.Status.ACTIVE, result.entitlementStatus());
        assertEquals(PAID_AT, result.effectiveFrom());
        assertEquals(PAID_AT.plusYears(1), result.effectiveUntil());
        assertEquals(SubscriptionHistory.ActionType.PURCHASED, result.historyAction());
        assertFalse(result.replayed());

        ArgumentCaptor<SoftwareContract> contractCaptor = ArgumentCaptor.forClass(SoftwareContract.class);
        ArgumentCaptor<SubscriptionEntitlement> entitlementCaptor =
                ArgumentCaptor.forClass(SubscriptionEntitlement.class);
        ArgumentCaptor<SubscriptionHistory> historyCaptor = ArgumentCaptor.forClass(SubscriptionHistory.class);
        verify(contractRepository).saveAndFlush(contractCaptor.capture());
        verify(entitlementRepository).saveAndFlush(entitlementCaptor.capture());
        verify(historyRepository).saveAndFlush(historyCaptor.capture());
        assertSame(contractCaptor.getValue(), entitlementCaptor.getValue().getContract());
        assertSame(contractCaptor.getValue(), historyCaptor.getValue().getContract());
        assertSame(fixture.transaction(), historyCaptor.getValue().getTransaction());
        assertEquals(SubscriptionOrderState.APPLIED, fixture.order().getStatus());
        verify(orderRepository).saveAndFlush(fixture.order());
        verify(auditService).append(any(FinancialAuditService.AuditCommand.class));
    }

    @Test
    void equivalentReplayReturnsStoredEvidenceWithoutDuplicatingApplicationEffects() {
        Fixture fixture = fixture("order-public");
        arrangeNewApplication(fixture);
        SubscriptionApplicationService.ApplicationResult first = service.applyPaidOrder(
                fixture.order().getPublicId(), fixture.transaction().getPublicId(), "apply-correlation");

        ArgumentCaptor<SoftwareContract> contractCaptor = ArgumentCaptor.forClass(SoftwareContract.class);
        ArgumentCaptor<SubscriptionEntitlement> entitlementCaptor =
                ArgumentCaptor.forClass(SubscriptionEntitlement.class);
        ArgumentCaptor<SubscriptionHistory> historyCaptor = ArgumentCaptor.forClass(SubscriptionHistory.class);
        verify(contractRepository).saveAndFlush(contractCaptor.capture());
        verify(entitlementRepository).saveAndFlush(entitlementCaptor.capture());
        verify(historyRepository).saveAndFlush(historyCaptor.capture());
        SoftwareContract contract = contractCaptor.getValue();
        SubscriptionEntitlement entitlement = entitlementCaptor.getValue();
        SubscriptionHistory history = historyCaptor.getValue();

        when(contractRepository.findByOrderId(40L)).thenReturn(Optional.of(contract));
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L)).thenReturn(Optional.of(entitlement));
        when(historyRepository.existsByOrderIdAndActionType(
                40L, SubscriptionHistory.ActionType.PURCHASED)).thenReturn(true);
        when(historyRepository.findByOrderIdOrderByOccurredAtAsc(40L)).thenReturn(List.of(history));

        SubscriptionApplicationService.ApplicationResult replay = service.applyPaidOrder(
                fixture.order().getPublicId(), fixture.transaction().getPublicId(), "replay-correlation");

        assertTrue(replay.replayed());
        assertEquals(first.contractPublicId(), replay.contractPublicId());
        assertEquals(SubscriptionOrderState.APPLIED, replay.orderStatus());
        verify(contractRepository, times(1)).saveAndFlush(any(SoftwareContract.class));
        verify(entitlementRepository, times(1)).saveAndFlush(any(SubscriptionEntitlement.class));
        verify(historyRepository, times(1)).saveAndFlush(any(SubscriptionHistory.class));
        verify(orderRepository, times(1)).saveAndFlush(fixture.order());
        verify(auditService, times(1)).append(any(FinancialAuditService.AuditCommand.class));
    }

    @Test
    void mismatchedTransactionOrderEvidenceIsRejectedBeforeAnyApplicationMutation() {
        Fixture fixture = fixture("paid-order");
        SubscriptionOrder differentOrder = paidOrder(
                "different-order", fixture.owner(), fixture.hotel(), fixture.plan());
        ReflectionTestUtils.setField(differentOrder, "id", 41L);
        when(orderRepository.findByPublicIdForUpdate("different-order"))
                .thenReturn(Optional.of(differentOrder));
        when(transactionRepository.findByPublicId(fixture.transaction().getPublicId()))
                .thenReturn(Optional.of(fixture.transaction()));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.applyPaidOrder(
                "different-order", fixture.transaction().getPublicId(), "mismatch-correlation"));

        assertEquals(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH, exception.code());
        verify(contractRepository, never()).saveAndFlush(any());
        verify(entitlementRepository, never()).saveAndFlush(any());
        verify(historyRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(auditService, never()).append(any());
    }

    private void arrangeNewApplication(Fixture fixture) {
        when(orderRepository.findByPublicIdForUpdate(fixture.order().getPublicId()))
                .thenReturn(Optional.of(fixture.order()));
        when(transactionRepository.findByPublicId(fixture.transaction().getPublicId()))
                .thenReturn(Optional.of(fixture.transaction()));
        when(contractRepository.findByOrderId(40L)).thenReturn(Optional.empty());
        when(contractRepository.findByTargetHotelIdAndStatusForUpdate(20L, SoftwareContract.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L)).thenReturn(Optional.empty());
        when(historyRepository.existsByOrderIdAndActionType(
                40L, SubscriptionHistory.ActionType.PURCHASED)).thenReturn(false);
        when(contractRepository.saveAndFlush(any(SoftwareContract.class))).thenAnswer(invocation -> {
            SoftwareContract contract = invocation.getArgument(0);
            ReflectionTestUtils.setField(contract, "id", 70L);
            return contract;
        });
        when(entitlementRepository.saveAndFlush(any(SubscriptionEntitlement.class))).thenAnswer(invocation -> {
            SubscriptionEntitlement entitlement = invocation.getArgument(0);
            ReflectionTestUtils.setField(entitlement, "id", 80L);
            return entitlement;
        });
        when(historyRepository.saveAndFlush(any(SubscriptionHistory.class))).thenAnswer(invocation -> {
            SubscriptionHistory history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "id", 90L);
            return history;
        });
        when(orderRepository.saveAndFlush(fixture.order())).thenReturn(fixture.order());
    }

    private Fixture fixture(String orderPublicId) {
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

        SubscriptionOrder order = paidOrder(orderPublicId, owner, hotel, plan);
        ReflectionTestUtils.setField(order, "id", 40L);

        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "SIMULATOR", PaymentEnvironment.SIMULATOR);
        configuration.configure(true, "****FORM", "env:PLATFORM_SIMULATOR", null, null, null);
        ReflectionTestUtils.setField(configuration, "id", 50L);

        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                "attempt-public",
                order,
                configuration,
                "MOMO",
                order.priceMoney(),
                "attempt-key",
                "attempt-hash",
                order.getExpiresAt());
        attempt.markPending("provider-order");
        attempt.markSucceeded("provider-transaction", "provider-event", PAID_AT);
        ReflectionTestUtils.setField(attempt, "id", 60L);

        PlatformFinancialTransaction transaction = PlatformFinancialTransaction.record(
                "transaction-public",
                order,
                attempt,
                null,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                PlatformFinancialTransaction.Direction.DEBIT,
                order.priceMoney(),
                attempt.getMethod(),
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                "provider-transaction",
                "platform-effect",
                "PROVIDER",
                null,
                "Verified platform payment",
                PAID_AT);
        ReflectionTestUtils.setField(transaction, "id", 61L);
        return new Fixture(owner, hotel, plan, order, transaction);
    }

    private SubscriptionOrder paidOrder(
            String publicId,
            User owner,
            Hotel hotel,
            SubscriptionPlan plan) {
        SubscriptionOrder order = SubscriptionOrder.create(
                publicId,
                "SUB-20260801-" + publicId,
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
                "key-" + publicId,
                "hash-" + publicId,
                PAID_AT.plusMinutes(30));
        order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, PAID_AT.minusMinutes(5));
        order.transitionTo(SubscriptionOrderState.PAID, PAID_AT);
        return order;
    }

    private record Fixture(
            User owner,
            Hotel hotel,
            SubscriptionPlan plan,
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction) {
    }
}

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
import com.hotel.platformbilling.order.SubscriptionOrderService;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.services.PropertyAccessService;
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
class SubscriptionRenewalServiceTest {

    private static final LocalDateTime CURRENT_START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime CURRENT_END = LocalDateTime.of(2026, 8, 31, 0, 0);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 8, 1, 6, 0);

    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformFinancialTransactionRepository transactionRepository;
    @Mock private PlatformSoftwareContractRepository contractRepository;
    @Mock private SubscriptionEntitlementRepository entitlementRepository;
    @Mock private PlatformSubscriptionHistoryRepository historyRepository;
    @Mock private SubscriptionOrderService orderService;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private FinancialAuditService auditService;

    private SubscriptionRenewalService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionRenewalService(
                orderRepository,
                transactionRepository,
                contractRepository,
                entitlementRepository,
                historyRepository,
                orderService,
                propertyAccessService,
                auditService,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createsRenewalOrderForTheCurrentEntitlementPlan() {
        Fixture fixture = fixture();
        SubscriptionOrderService.OrderResponse expected = response(fixture.renewalOrder());
        when(propertyAccessService.requireAssignedHotel(20L)).thenReturn(fixture.hotel());
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L))
                .thenReturn(Optional.of(fixture.entitlement()));
        when(orderService.createLifecycleOrder(any())).thenReturn(expected);

        SubscriptionOrderService.OrderResponse result = service.createRenewalOrder(
                new SubscriptionRenewalService.RenewalOrderCommand(20L, "renew-key"));

        assertSame(expected, result);
        ArgumentCaptor<SubscriptionOrderService.CreateLifecycleOrderCommand> commandCaptor =
                ArgumentCaptor.forClass(SubscriptionOrderService.CreateLifecycleOrderCommand.class);
        verify(orderService).createLifecycleOrder(commandCaptor.capture());
        assertEquals(20L, commandCaptor.getValue().targetHotelId());
        assertEquals(30L, commandCaptor.getValue().planId());
        assertEquals(SubscriptionOrder.Operation.RENEW, commandCaptor.getValue().operation());
        assertEquals("renew-key", commandCaptor.getValue().idempotencyKey());
    }

    @Test
    void lifetimeEntitlementIsBlockedBeforeAProviderOrderCanBeCreated() {
        Fixture fixture = fixture();
        ReflectionTestUtils.setField(fixture.currentContract(), "lifetime", true);
        ReflectionTestUtils.setField(fixture.currentContract(), "effectiveUntil", null);
        ReflectionTestUtils.setField(fixture.entitlement(), "lifetime", true);
        ReflectionTestUtils.setField(fixture.entitlement(), "effectiveUntil", null);
        when(propertyAccessService.requireAssignedHotel(20L)).thenReturn(fixture.hotel());
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L))
                .thenReturn(Optional.of(fixture.entitlement()));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.createRenewalOrder(
                new SubscriptionRenewalService.RenewalOrderCommand(20L, "renew-key")));

        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, exception.code());
        verify(orderService, never()).createLifecycleOrder(any());
    }

    @Test
    void paidRenewalExtendsFromCurrentEndUsingSnapshotDurationAndReplaysWithoutDuplicates() {
        Fixture fixture = fixture();
        arrangeApplication(fixture);

        SubscriptionRenewalService.RenewalApplicationResult first = service.applyPaidRenewal(
                fixture.renewalOrder().getPublicId(),
                fixture.renewalTransaction().getPublicId(),
                "renew-correlation");

        assertEquals(SubscriptionOrderState.APPLIED, first.orderStatus());
        assertEquals(CURRENT_START, first.effectiveFrom());
        assertEquals(CURRENT_END.plusMonths(2), first.effectiveUntil());
        assertEquals(SubscriptionHistory.ActionType.RENEWED, first.historyAction());
        assertFalse(first.replayed());

        ArgumentCaptor<SoftwareContract> contractCaptor = ArgumentCaptor.forClass(SoftwareContract.class);
        ArgumentCaptor<SubscriptionEntitlement> entitlementCaptor =
                ArgumentCaptor.forClass(SubscriptionEntitlement.class);
        ArgumentCaptor<SubscriptionHistory> historyCaptor = ArgumentCaptor.forClass(SubscriptionHistory.class);
        verify(contractRepository, times(2)).saveAndFlush(contractCaptor.capture());
        verify(entitlementRepository).saveAndFlush(entitlementCaptor.capture());
        verify(historyRepository).saveAndFlush(historyCaptor.capture());
        SoftwareContract renewedContract = contractCaptor.getAllValues().get(0);
        SubscriptionEntitlement renewedEntitlement = entitlementCaptor.getValue();
        SubscriptionHistory history = historyCaptor.getValue();
        assertSame(fixture.currentContract(), renewedContract.getSupersedesContract());
        assertEquals(SoftwareContract.Status.SUPERSEDED, fixture.currentContract().getStatus());
        assertSame(renewedContract, renewedEntitlement.getContract());
        assertSame(renewedContract, history.getContract());
        assertTrue(history.getPreviousStateJson().contains("current-contract"));

        when(contractRepository.findByOrderId(100L)).thenReturn(Optional.of(renewedContract));
        when(historyRepository.existsByOrderIdAndActionType(
                100L, SubscriptionHistory.ActionType.RENEWED)).thenReturn(true);
        when(historyRepository.findByOrderIdOrderByOccurredAtAsc(100L)).thenReturn(List.of(history));

        SubscriptionRenewalService.RenewalApplicationResult replay = service.applyPaidRenewal(
                fixture.renewalOrder().getPublicId(),
                fixture.renewalTransaction().getPublicId(),
                "renew-replay");

        assertTrue(replay.replayed());
        assertEquals(first.contractPublicId(), replay.contractPublicId());
        verify(contractRepository, times(2)).saveAndFlush(any(SoftwareContract.class));
        verify(entitlementRepository, times(1)).saveAndFlush(any(SubscriptionEntitlement.class));
        verify(historyRepository, times(1)).saveAndFlush(any(SubscriptionHistory.class));
        verify(orderRepository, times(1)).saveAndFlush(fixture.renewalOrder());
        verify(auditService, times(1)).append(any(FinancialAuditService.AuditCommand.class));
    }

    private void arrangeApplication(Fixture fixture) {
        when(orderRepository.findByPublicIdForUpdate(fixture.renewalOrder().getPublicId()))
                .thenReturn(Optional.of(fixture.renewalOrder()));
        when(transactionRepository.findByPublicId(fixture.renewalTransaction().getPublicId()))
                .thenReturn(Optional.of(fixture.renewalTransaction()));
        when(contractRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L))
                .thenReturn(Optional.of(fixture.entitlement()));
        when(historyRepository.existsByOrderIdAndActionType(
                100L, SubscriptionHistory.ActionType.RENEWED)).thenReturn(false);
        when(contractRepository.saveAndFlush(any(SoftwareContract.class))).thenAnswer(invocation -> {
            SoftwareContract contract = invocation.getArgument(0);
            if (contract.getId() == null) {
                ReflectionTestUtils.setField(contract, "id", 110L);
            }
            return contract;
        });
        when(entitlementRepository.saveAndFlush(any(SubscriptionEntitlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.saveAndFlush(any(SubscriptionHistory.class))).thenAnswer(invocation -> {
            SubscriptionHistory history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "id", 120L);
            return history;
        });
        when(orderRepository.saveAndFlush(fixture.renewalOrder())).thenReturn(fixture.renewalOrder());
    }

    private Fixture fixture() {
        User owner = new User();
        owner.setId(10L);

        Hotel hotel = new Hotel();
        hotel.setId(20L);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(30L);
        plan.setCode("PRO");
        plan.setNameVi("Professional Monthly");
        plan.setBillingType("MONTHLY");
        plan.setPrice(VndMoney.of(250_000).amount());
        plan.setStatus("ACTIVE");

        SubscriptionOrder purchaseOrder = order(
                "purchase-order", owner, hotel, plan, SubscriptionOrder.Operation.PURCHASE, 1,
                SubscriptionOrder.DurationUnit.MONTH);
        ReflectionTestUtils.setField(purchaseOrder, "id", 40L);
        purchaseOrder.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, CURRENT_START.minusMinutes(10));
        purchaseOrder.transitionTo(SubscriptionOrderState.PAID, CURRENT_START);

        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "SIMULATOR", PaymentEnvironment.SIMULATOR);
        configuration.configure(true, "****FORM", "env:PLATFORM_SIMULATOR", null, null, null);
        ReflectionTestUtils.setField(configuration, "id", 50L);

        PlatformPaymentAttempt purchaseAttempt = successfulAttempt(
                "purchase-attempt", purchaseOrder, configuration, CURRENT_START);
        ReflectionTestUtils.setField(purchaseAttempt, "id", 51L);
        PlatformFinancialTransaction purchaseTransaction = transaction(
                "purchase-transaction",
                purchaseOrder,
                purchaseAttempt,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                CURRENT_START);
        ReflectionTestUtils.setField(purchaseTransaction, "id", 52L);
        purchaseOrder.transitionTo(SubscriptionOrderState.APPLIED, CURRENT_START);

        SoftwareContract currentContract = SoftwareContract.activate(
                "current-contract",
                purchaseOrder,
                purchaseTransaction,
                null,
                "{\"plan\":\"PRO\"}",
                "{\"features\":[]}",
                CURRENT_START,
                CURRENT_END,
                false,
                VndMoney.of(250_000));
        ReflectionTestUtils.setField(currentContract, "id", 70L);
        SubscriptionEntitlement entitlement = SubscriptionEntitlement.activate(currentContract);
        ReflectionTestUtils.setField(entitlement, "id", 80L);

        SubscriptionOrder renewalOrder = order(
                "renewal-order", owner, hotel, plan, SubscriptionOrder.Operation.RENEW, 2,
                SubscriptionOrder.DurationUnit.MONTH);
        ReflectionTestUtils.setField(renewalOrder, "id", 100L);
        renewalOrder.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, PAID_AT.minusMinutes(5));
        renewalOrder.transitionTo(SubscriptionOrderState.PAID, PAID_AT);
        PlatformPaymentAttempt renewalAttempt = successfulAttempt(
                "renewal-attempt", renewalOrder, configuration, PAID_AT);
        ReflectionTestUtils.setField(renewalAttempt, "id", 101L);
        PlatformFinancialTransaction renewalTransaction = transaction(
                "renewal-transaction",
                renewalOrder,
                renewalAttempt,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_RENEWAL,
                PAID_AT);
        ReflectionTestUtils.setField(renewalTransaction, "id", 102L);
        return new Fixture(
                owner,
                hotel,
                plan,
                currentContract,
                entitlement,
                renewalOrder,
                renewalTransaction);
    }

    private SubscriptionOrder order(
            String publicId,
            User owner,
            Hotel hotel,
            SubscriptionPlan plan,
            SubscriptionOrder.Operation operation,
            int durationValue,
            SubscriptionOrder.DurationUnit durationUnit) {
        return SubscriptionOrder.create(
                publicId,
                "SUB-20260801-" + publicId,
                owner,
                hotel,
                operation,
                plan,
                "PLAN-30-V1",
                "PRO",
                "Professional Monthly",
                VndMoney.of(250_000),
                "MONTHLY",
                durationValue,
                durationUnit,
                "{\"features\":[]}",
                "key-" + publicId,
                "hash-" + publicId,
                PAID_AT.plusMinutes(30));
    }

    private PlatformPaymentAttempt successfulAttempt(
            String publicId,
            SubscriptionOrder order,
            PlatformPaymentConfiguration configuration,
            LocalDateTime occurredAt) {
        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                publicId,
                order,
                configuration,
                "MOMO",
                order.priceMoney(),
                "key-" + publicId,
                "hash-" + publicId,
                order.getExpiresAt());
        attempt.markPending("provider-" + publicId);
        attempt.markSucceeded("txn-" + publicId, "event-" + publicId, occurredAt);
        return attempt;
    }

    private PlatformFinancialTransaction transaction(
            String publicId,
            SubscriptionOrder order,
            PlatformPaymentAttempt attempt,
            PlatformFinancialTransaction.TransactionType type,
            LocalDateTime occurredAt) {
        return PlatformFinancialTransaction.record(
                publicId,
                order,
                attempt,
                null,
                type,
                PlatformFinancialTransaction.Direction.DEBIT,
                order.priceMoney(),
                attempt.getMethod(),
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                "txn-" + publicId,
                "effect-" + publicId,
                "PROVIDER",
                null,
                "Verified platform payment",
                occurredAt);
    }

    private SubscriptionOrderService.OrderResponse response(SubscriptionOrder order) {
        return new SubscriptionOrderService.OrderResponse(
                order.getId(),
                order.getPublicId(),
                order.getOrderCode(),
                order.getOwner().getId(),
                order.getTargetHotel().getId(),
                order.getOperation(),
                order.getPlan().getId(),
                order.getPlanVersion(),
                order.getPlanCode(),
                order.getPlanName(),
                order.getPrice(),
                order.getCurrency(),
                order.getBillingPeriod(),
                order.getDurationValue(),
                order.getDurationUnit(),
                order.getFeatureSnapshotJson(),
                order.getStatus(),
                order.getExpiresAt(),
                false);
    }

    private record Fixture(
            User owner,
            Hotel hotel,
            SubscriptionPlan plan,
            SoftwareContract currentContract,
            SubscriptionEntitlement entitlement,
            SubscriptionOrder renewalOrder,
            PlatformFinancialTransaction renewalTransaction) {
    }
}

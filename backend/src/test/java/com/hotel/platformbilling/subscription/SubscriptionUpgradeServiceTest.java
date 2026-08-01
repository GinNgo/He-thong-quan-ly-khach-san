package com.hotel.platformbilling.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.PlanFeature;
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
import com.hotel.platformbilling.order.PlatformSubscriptionPlanCatalogRepository;
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
import java.util.LinkedHashSet;
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
class SubscriptionUpgradeServiceTest {

    private static final LocalDateTime CURRENT_START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime CURRENT_END = LocalDateTime.of(2026, 8, 31, 0, 0);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 8, 1, 6, 0);

    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformFinancialTransactionRepository transactionRepository;
    @Mock private PlatformSoftwareContractRepository contractRepository;
    @Mock private SubscriptionEntitlementRepository entitlementRepository;
    @Mock private PlatformSubscriptionHistoryRepository historyRepository;
    @Mock private PlatformSubscriptionPlanCatalogRepository planRepository;
    @Mock private SubscriptionOrderService orderService;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private PlatformSubscriptionUsageRepository usageRepository;
    @Mock private FinancialAuditService auditService;

    private SubscriptionUpgradeService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionUpgradeService(
                orderRepository,
                transactionRepository,
                contractRepository,
                entitlementRepository,
                historyRepository,
                planRepository,
                orderService,
                propertyAccessService,
                usageRepository,
                auditService,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createsFullCatalogPriceUpgradeOrderForStrictlyHigherLimitsWithinCurrentUsage() {
        Fixture fixture = fixture();
        when(propertyAccessService.requireManagedHotel(20L)).thenReturn(fixture.hotel());
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L))
                .thenReturn(Optional.of(fixture.entitlement()));
        when(planRepository.findByIdForSnapshot(31L)).thenReturn(Optional.of(fixture.targetPlan()));
        arrangeUsage(1, 4, 40, 25, 5);
        SubscriptionOrderService.OrderResponse expected = response(fixture.upgradeOrder());
        when(orderService.createLifecycleOrder(any())).thenReturn(expected);

        SubscriptionOrderService.OrderResponse result = service.createUpgradeOrder(
                new SubscriptionUpgradeService.UpgradeOrderCommand(20L, 31L, "upgrade-key"));

        assertSame(expected, result);
        ArgumentCaptor<SubscriptionOrderService.CreateLifecycleOrderCommand> commandCaptor =
                ArgumentCaptor.forClass(SubscriptionOrderService.CreateLifecycleOrderCommand.class);
        verify(orderService).createLifecycleOrder(commandCaptor.capture());
        assertEquals(20L, commandCaptor.getValue().targetHotelId());
        assertEquals(31L, commandCaptor.getValue().planId());
        assertEquals(SubscriptionOrder.Operation.UPGRADE, commandCaptor.getValue().operation());
        assertEquals("upgrade-key", commandCaptor.getValue().idempotencyKey());
    }

    @Test
    void rejectsTargetPlanThatReducesAnExistingFeatureLimit() {
        Fixture fixture = fixture();
        fixture.targetPlan().getFeatures().stream()
                .filter(feature -> "MAX_ROOMS".equals(feature.getFeatureCode()))
                .findFirst()
                .orElseThrow()
                .setLimitValue(5);
        when(propertyAccessService.requireManagedHotel(20L)).thenReturn(fixture.hotel());
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L))
                .thenReturn(Optional.of(fixture.entitlement()));
        when(planRepository.findByIdForSnapshot(31L)).thenReturn(Optional.of(fixture.targetPlan()));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.createUpgradeOrder(
                new SubscriptionUpgradeService.UpgradeOrderCommand(20L, 31L, "upgrade-key")));

        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, exception.code());
        verify(orderService, never()).createLifecycleOrder(any());
    }

    @Test
    void rejectsUpgradeWhenCurrentUsageAlreadyExceedsTheTargetLimit() {
        Fixture fixture = fixture();
        when(propertyAccessService.requireManagedHotel(20L)).thenReturn(fixture.hotel());
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L))
                .thenReturn(Optional.of(fixture.entitlement()));
        when(planRepository.findByIdForSnapshot(31L)).thenReturn(Optional.of(fixture.targetPlan()));
        when(usageRepository.countActiveOwnedProperties(10L)).thenReturn(1L);
        when(usageRepository.countRoomTypes(20L)).thenReturn(4L);
        when(usageRepository.countRooms(20L)).thenReturn(101L);

        FinancialException exception = assertThrows(FinancialException.class, () -> service.createUpgradeOrder(
                new SubscriptionUpgradeService.UpgradeOrderCommand(20L, 31L, "upgrade-key")));

        assertEquals(FinancialErrorCode.INVALID_STATE_TRANSITION, exception.code());
        verify(orderService, never()).createLifecycleOrder(any());
    }

    @Test
    void paidUpgradeActivatesImmediatelyPreservesRemainingTermAndReplaysWithoutDuplicates() {
        Fixture fixture = fixture();
        arrangeApplication(fixture);

        SubscriptionUpgradeService.UpgradeApplicationResult first = service.applyPaidUpgrade(
                fixture.upgradeOrder().getPublicId(),
                fixture.upgradeTransaction().getPublicId(),
                "upgrade-correlation");

        assertEquals(SubscriptionOrderState.APPLIED, first.orderStatus());
        assertEquals(PAID_AT, first.effectiveFrom());
        assertEquals(CURRENT_END.plusYears(1), first.effectiveUntil());
        assertEquals(SubscriptionHistory.ActionType.UPGRADED, first.historyAction());
        assertFalse(first.replayed());

        ArgumentCaptor<SoftwareContract> contractCaptor = ArgumentCaptor.forClass(SoftwareContract.class);
        ArgumentCaptor<SubscriptionEntitlement> entitlementCaptor =
                ArgumentCaptor.forClass(SubscriptionEntitlement.class);
        ArgumentCaptor<SubscriptionHistory> historyCaptor = ArgumentCaptor.forClass(SubscriptionHistory.class);
        verify(contractRepository, times(2)).saveAndFlush(contractCaptor.capture());
        verify(entitlementRepository).saveAndFlush(entitlementCaptor.capture());
        verify(historyRepository).saveAndFlush(historyCaptor.capture());
        SoftwareContract upgradedContract = contractCaptor.getAllValues().get(0);
        SubscriptionEntitlement upgradedEntitlement = entitlementCaptor.getValue();
        SubscriptionHistory history = historyCaptor.getValue();
        assertSame(fixture.currentContract(), upgradedContract.getSupersedesContract());
        assertEquals(fixture.upgradeOrder().getPrice(), upgradedContract.getContractValue());
        assertEquals(SoftwareContract.Status.SUPERSEDED, fixture.currentContract().getStatus());
        assertSame(upgradedContract, upgradedEntitlement.getContract());
        assertSame(upgradedContract, history.getContract());
        assertTrue(history.getReason().contains("FULL_PRICE_PRESERVE_REMAINING_TERM_V1"));

        when(contractRepository.findByOrderId(100L)).thenReturn(Optional.of(upgradedContract));
        when(historyRepository.existsByOrderIdAndActionType(
                100L, SubscriptionHistory.ActionType.UPGRADED)).thenReturn(true);
        when(historyRepository.findByOrderIdOrderByOccurredAtAsc(100L)).thenReturn(List.of(history));

        SubscriptionUpgradeService.UpgradeApplicationResult replay = service.applyPaidUpgrade(
                fixture.upgradeOrder().getPublicId(),
                fixture.upgradeTransaction().getPublicId(),
                "upgrade-replay");

        assertTrue(replay.replayed());
        assertEquals(first.contractPublicId(), replay.contractPublicId());
        verify(contractRepository, times(2)).saveAndFlush(any(SoftwareContract.class));
        verify(entitlementRepository, times(1)).saveAndFlush(any(SubscriptionEntitlement.class));
        verify(historyRepository, times(1)).saveAndFlush(any(SubscriptionHistory.class));
        verify(orderRepository, times(1)).saveAndFlush(fixture.upgradeOrder());
        verify(auditService, times(1)).append(any(FinancialAuditService.AuditCommand.class));
    }

    private void arrangeApplication(Fixture fixture) {
        when(orderRepository.findByPublicIdForUpdate(fixture.upgradeOrder().getPublicId()))
                .thenReturn(Optional.of(fixture.upgradeOrder()));
        when(transactionRepository.findByPublicId(fixture.upgradeTransaction().getPublicId()))
                .thenReturn(Optional.of(fixture.upgradeTransaction()));
        when(contractRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(entitlementRepository.findByTargetHotelIdForUpdate(20L))
                .thenReturn(Optional.of(fixture.entitlement()));
        when(historyRepository.existsByOrderIdAndActionType(
                100L, SubscriptionHistory.ActionType.UPGRADED)).thenReturn(false);
        arrangeUsage(1, 4, 40, 25, 5);
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
        when(orderRepository.saveAndFlush(fixture.upgradeOrder())).thenReturn(fixture.upgradeOrder());
    }

    private void arrangeUsage(long properties, long roomTypes, long rooms, long images, long staff) {
        when(usageRepository.countActiveOwnedProperties(10L)).thenReturn(properties);
        when(usageRepository.countRoomTypes(20L)).thenReturn(roomTypes);
        when(usageRepository.countRooms(20L)).thenReturn(rooms);
        when(usageRepository.countPropertyImages(20L)).thenReturn(images / 3);
        when(usageRepository.countRoomTypeImages(20L)).thenReturn(images / 3);
        when(usageRepository.countRoomImages(20L)).thenReturn(images - (images / 3) * 2);
        when(usageRepository.countActiveStaff(20L)).thenReturn(staff);
    }

    private Fixture fixture() {
        User owner = new User();
        owner.setId(10L);

        Hotel hotel = new Hotel();
        hotel.setId(20L);

        SubscriptionPlan currentPlan = plan(30L, "BASIC", "Basic", 250_000, false,
                new FeatureLimit("MAX_PROPERTIES", 1),
                new FeatureLimit("MAX_ROOM_TYPES", 10),
                new FeatureLimit("MAX_ROOMS", 50),
                new FeatureLimit("MAX_IMAGES", 50),
                new FeatureLimit("MAX_STAFF", 5));
        SubscriptionPlan targetPlan = plan(31L, "PRO", "Professional", 2_400_000, false,
                new FeatureLimit("MAX_PROPERTIES", 3),
                new FeatureLimit("MAX_ROOM_TYPES", 20),
                new FeatureLimit("MAX_ROOMS", 100),
                new FeatureLimit("MAX_IMAGES", 300),
                new FeatureLimit("MAX_STAFF", 10));

        SubscriptionOrder purchaseOrder = order(
                "purchase-order", owner, hotel, currentPlan, SubscriptionOrder.Operation.PURCHASE,
                1, SubscriptionOrder.DurationUnit.MONTH, currentSnapshot());
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
                "{\"plan\":\"BASIC\"}",
                currentSnapshot(),
                CURRENT_START,
                CURRENT_END,
                false,
                VndMoney.of(250_000));
        ReflectionTestUtils.setField(currentContract, "id", 70L);
        SubscriptionEntitlement entitlement = SubscriptionEntitlement.activate(currentContract);
        ReflectionTestUtils.setField(entitlement, "id", 80L);

        SubscriptionOrder upgradeOrder = order(
                "upgrade-order", owner, hotel, targetPlan, SubscriptionOrder.Operation.UPGRADE,
                1, SubscriptionOrder.DurationUnit.YEAR, targetSnapshot());
        ReflectionTestUtils.setField(upgradeOrder, "id", 100L);
        upgradeOrder.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, PAID_AT.minusMinutes(5));
        upgradeOrder.transitionTo(SubscriptionOrderState.PAID, PAID_AT);
        PlatformPaymentAttempt upgradeAttempt = successfulAttempt(
                "upgrade-attempt", upgradeOrder, configuration, PAID_AT);
        ReflectionTestUtils.setField(upgradeAttempt, "id", 101L);
        PlatformFinancialTransaction upgradeTransaction = transaction(
                "upgrade-transaction",
                upgradeOrder,
                upgradeAttempt,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_UPGRADE,
                PAID_AT);
        ReflectionTestUtils.setField(upgradeTransaction, "id", 102L);
        return new Fixture(
                owner,
                hotel,
                currentPlan,
                targetPlan,
                currentContract,
                entitlement,
                upgradeOrder,
                upgradeTransaction);
    }

    private SubscriptionPlan plan(
            Long id,
            String code,
            String name,
            long price,
            boolean lifetime,
            FeatureLimit... limits) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setCode(code);
        plan.setNameVi(name);
        plan.setNameEn(name);
        plan.setBillingType(lifetime ? "ONCE" : "YEARLY");
        plan.setPrice(VndMoney.of(price).amount());
        plan.setIsLifetime(lifetime);
        plan.setStatus("ACTIVE");
        plan.setFeatures(new LinkedHashSet<>());
        for (FeatureLimit limit : limits) {
            PlanFeature feature = new PlanFeature();
            feature.setPlan(plan);
            feature.setFeatureCode(limit.code());
            feature.setLimitValue(limit.limit());
            plan.getFeatures().add(feature);
        }
        return plan;
    }

    private SubscriptionOrder order(
            String publicId,
            User owner,
            Hotel hotel,
            SubscriptionPlan plan,
            SubscriptionOrder.Operation operation,
            int durationValue,
            SubscriptionOrder.DurationUnit durationUnit,
            String featureSnapshot) {
        return SubscriptionOrder.create(
                publicId,
                "SUB-20260801-" + publicId,
                owner,
                hotel,
                operation,
                plan,
                "PLAN-" + plan.getId() + "-V1",
                plan.getCode(),
                plan.getNameVi(),
                VndMoney.of(plan.getPrice()),
                plan.getBillingType(),
                durationValue,
                durationUnit,
                featureSnapshot,
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

    private String currentSnapshot() {
        return "{\"features\":["
                + "{\"code\":\"MAX_PROPERTIES\",\"limit\":1},"
                + "{\"code\":\"MAX_ROOM_TYPES\",\"limit\":10},"
                + "{\"code\":\"MAX_ROOMS\",\"limit\":50},"
                + "{\"code\":\"MAX_IMAGES\",\"limit\":50},"
                + "{\"code\":\"MAX_STAFF\",\"limit\":5}]}";
    }

    private String targetSnapshot() {
        return "{\"features\":["
                + "{\"code\":\"MAX_PROPERTIES\",\"limit\":3},"
                + "{\"code\":\"MAX_ROOM_TYPES\",\"limit\":20},"
                + "{\"code\":\"MAX_ROOMS\",\"limit\":100},"
                + "{\"code\":\"MAX_IMAGES\",\"limit\":300},"
                + "{\"code\":\"MAX_STAFF\",\"limit\":10}]}";
    }

    private record FeatureLimit(String code, int limit) {
    }

    private record Fixture(
            User owner,
            Hotel hotel,
            SubscriptionPlan currentPlan,
            SubscriptionPlan targetPlan,
            SoftwareContract currentContract,
            SubscriptionEntitlement entitlement,
            SubscriptionOrder upgradeOrder,
            PlatformFinancialTransaction upgradeTransaction) {
    }
}

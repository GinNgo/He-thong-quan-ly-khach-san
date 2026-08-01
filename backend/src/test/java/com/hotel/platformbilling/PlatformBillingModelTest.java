package com.hotel.platformbilling;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.subscription.SoftwareContract;
import com.hotel.platformbilling.subscription.SubscriptionEntitlement;
import com.hotel.platformbilling.subscription.SubscriptionHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformBillingModelTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 2, 0);

    @Test
    void orderKeepsBackendSnapshotsAndUsesExplicitLifecycleTransitions() {
        Fixture fixture = fixture();
        SubscriptionOrder order = order(fixture);

        assertEquals("PRO-2026", order.getPlanVersion());
        assertEquals("PRO", order.getPlanCode());
        assertEquals(VndMoney.of(2_400_000), order.priceMoney());
        assertFalse(order.expiredAt(NOW));

        order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, NOW);
        order.transitionTo(SubscriptionOrderState.PAID, NOW.plusMinutes(1));
        order.transitionTo(SubscriptionOrderState.APPLIED, NOW.plusMinutes(2));

        assertEquals(SubscriptionOrderState.APPLIED, order.getStatus());
        assertEquals(NOW.plusMinutes(2), order.getAppliedAt());
        assertThrows(IllegalStateException.class,
                () -> order.transitionTo(SubscriptionOrderState.CANCELLED, NOW.plusMinutes(3)));
    }

    @Test
    void paymentLedgerContractEntitlementAndHistoryStayInPlatformScope() {
        Fixture fixture = fixture();
        SubscriptionOrder order = order(fixture);
        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "simulator", PaymentEnvironment.SIMULATOR);
        configuration.configure(true, "****PLATFORM", null, null, null, null);

        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                "platform-attempt-1",
                order,
                configuration,
                "qr",
                VndMoney.of(2_400_000),
                "attempt-key-1",
                "attempt-hash-1",
                NOW.plusMinutes(15));
        attempt.markPending("provider-order-1");
        attempt.markSucceeded("provider-txn-1", "provider-event-1", NOW.plusMinutes(1));

        PlatformFinancialTransaction transaction = PlatformFinancialTransaction.record(
                "platform-transaction-1",
                order,
                attempt,
                null,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                PlatformFinancialTransaction.Direction.DEBIT,
                VndMoney.of(2_400_000),
                "qr",
                "simulator",
                PaymentEnvironment.SIMULATOR,
                "provider-txn-1",
                "platform-effect-1",
                "PROVIDER",
                null,
                "Verified simulator payment",
                NOW.plusMinutes(1));

        SoftwareContract contract = SoftwareContract.activate(
                "platform-contract-1",
                order,
                transaction,
                null,
                "{\"code\":\"PRO\"}",
                "{\"REPORT_EXPORT\":true}",
                NOW.plusMinutes(2),
                NOW.plusYears(1),
                false,
                VndMoney.of(2_400_000));
        SubscriptionEntitlement entitlement = SubscriptionEntitlement.activate(contract);
        SubscriptionHistory history = SubscriptionHistory.record(
                order,
                contract,
                transaction,
                SubscriptionHistory.ActionType.PURCHASED,
                null,
                "{\"plan\":\"PRO\",\"status\":\"ACTIVE\"}",
                "SYSTEM",
                fixture.owner().getId(),
                "Initial purchase",
                NOW.plusMinutes(2));

        assertEquals(PlatformPaymentAttempt.Status.SUCCESS, attempt.getStatus());
        assertEquals(PlatformFinancialTransaction.Direction.DEBIT, transaction.getDirection());
        assertEquals(SoftwareContract.Status.ACTIVE, contract.getStatus());
        assertEquals(SubscriptionEntitlement.Status.ACTIVE, entitlement.getStatus());
        assertEquals(SubscriptionHistory.ActionType.PURCHASED, history.getActionType());
        assertTrue(entitlement.isLifetime() == contract.isLifetime());
    }

    @Test
    void productionConfigurationFailsClosedWithoutApprovalEvidence() {
        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "vnpay", PaymentEnvironment.PRODUCTION);

        assertThrows(IllegalStateException.class, () -> configuration.configure(
                true,
                "****MERCHANT",
                "secret://platform/vnpay",
                null,
                null,
                "https://payments.example.test/callback"));
    }

    private static SubscriptionOrder order(Fixture fixture) {
        return SubscriptionOrder.create(
                "platform-order-1",
                "SUB-2026-0001",
                fixture.owner(),
                fixture.hotel(),
                SubscriptionOrder.Operation.PURCHASE,
                fixture.plan(),
                "PRO-2026",
                "PRO",
                "Professional",
                VndMoney.of(2_400_000),
                "YEARLY",
                12,
                SubscriptionOrder.DurationUnit.MONTH,
                "{\"REPORT_EXPORT\":true}",
                "order-key-1",
                "order-hash-1",
                NOW.plusMinutes(30));
    }

    private static Fixture fixture() {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");

        Hotel hotel = new Hotel();
        hotel.setId(20L);
        hotel.setName("Platform Fixture Hotel");

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(30L);
        plan.setCode("PRO");
        plan.setNameVi("Chuyen nghiep");
        plan.setNameEn("Professional");
        plan.setBillingType("YEARLY");
        plan.setPrice(VndMoney.of(2_400_000).amount());
        plan.setStatus("ACTIVE");

        return new Fixture(owner, hotel, plan);
    }

    private record Fixture(User owner, Hotel hotel, SubscriptionPlan plan) {
    }
}

package com.hotel.platformbilling.refund;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformRefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T03:00:00Z");

    @Mock private PlatformFinancialTransactionRepository transactionRepository;
    @Mock private PlatformRefundRequestRepository requestRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private FinancialAuditService auditService;

    private User owner;
    private PlatformFinancialTransaction original;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(7L);
        Hotel hotel = new Hotel();
        hotel.setId(10L);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(5L);
        SubscriptionOrder order = SubscriptionOrder.create(
                "platform-order", "SUB-20260802-TEST", owner, hotel, SubscriptionOrder.Operation.PURCHASE,
                plan, "PLAN-V1", "PRO", "Pro", VndMoney.of(2_000_000), "YEARLY", 1,
                SubscriptionOrder.DurationUnit.YEAR, "{}", "order-idempotency", "order-hash",
                LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC));
        ReflectionTestUtils.setField(order, "id", 30L);
        original = PlatformFinancialTransaction.record(
                "platform-transaction", order, null, null,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                PlatformFinancialTransaction.Direction.DEBIT, VndMoney.of(2_000_000),
                "SIMULATOR", "SIMULATOR", PaymentEnvironment.SIMULATOR, "provider-payment",
                "platform-payment-effect", "PROVIDER", null, "Subscription purchase",
                LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        ReflectionTestUtils.setField(original, "id", 100L);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
    }

    @Test
    void missingEntitlementPolicyRecordsBlockerBeforeProviderOrLedgerMutation() {
        PlatformRefundService service = service(List.of(), "");
        arrangeEmptyBalance();
        when(requestRepository.saveAndFlush(any(PlatformRefundRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformRefundService.RefundResult result = service.request(new PlatformRefundService.RequestCommand(
                original.getPublicId(), BigDecimal.valueOf(500_000), "Subscription cancellation",
                "platform-refund-request", "platform-refund-correlation"));

        assertEquals(RefundState.POLICY_BLOCKED, result.status());
        assertFalse(result.policyAvailable());
        assertEquals(0, result.remainingRefundableAmount().compareTo(BigDecimal.valueOf(2_000_000)));
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvedVersionMustHaveARegisteredPolicyHandler() {
        PlatformRefundRequest blocked = PlatformRefundRequest.request(
                original, original.getOrder(), VndMoney.of(500_000), "Subscription cancellation", owner,
                "FULL_REFUND_V1", "refund-key", "refund-hash", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(blocked, "id", 200L);
        when(requestRepository.findByPublicIdForUpdate(blocked.getPublicId())).thenReturn(Optional.of(blocked));
        PlatformRefundService service = service(List.of(), "FULL_REFUND_V1");

        FinancialException exception = assertThrows(FinancialException.class,
                () -> service.approve(blocked.getPublicId(), "approval-correlation"));
        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, exception.code());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    private PlatformRefundService service(List<PlatformRefundEntitlementPolicy> policies, String version) {
        return new PlatformRefundService(transactionRepository, requestRepository, propertyAccessService,
                auditService, policies, version, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void arrangeEmptyBalance() {
        when(transactionRepository.findByPublicIdForUpdate(original.getPublicId())).thenReturn(Optional.of(original));
        when(requestRepository.findByRequestedByIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(requestRepository.findByOriginalTransactionOrderByRequestedAtAsc(original)).thenReturn(List.of());
        when(transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(100L)).thenReturn(List.of());
    }
}

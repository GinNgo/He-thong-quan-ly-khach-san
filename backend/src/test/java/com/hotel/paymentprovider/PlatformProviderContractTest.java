package com.hotel.paymentprovider;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.adapters.SimulatorPaymentProviderAdapter;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
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
import com.hotel.platformbilling.subscription.SubscriptionUpgradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformProviderContractTest {

    private static final Instant NOW = Instant.parse("2026-08-01T11:00:00Z");
    private static final String MERCHANT = "LUXESTAY_PLATFORM";
    private static final String SECRET = "platform-simulator-signing-secret-32-bytes";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(2_400_000);
    private static final String REFERENCE = "platform-order-ref";

    @Mock private PlatformPaymentAttemptRepository attemptRepository;
    @Mock private PlatformFinancialTransactionRepository transactionRepository;
    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformPaymentConfigurationService configurationService;
    @Mock private FinancialAuditService auditService;
    @Mock private SubscriptionApplicationService applicationService;
    @Mock private SubscriptionRenewalService renewalService;
    @Mock private SubscriptionUpgradeService upgradeService;

    private PlatformPaymentCallbackService service;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = fixture();
        PaymentProviderAdapterRegistry registry = new PaymentProviderAdapterRegistry(
                List.of(new SimulatorPaymentProviderAdapter()));
        service = new PlatformPaymentCallbackService(
                attemptRepository,
                transactionRepository,
                orderRepository,
                configurationService,
                registry,
                auditService,
                applicationService,
                renewalService,
                upgradeService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsAValidSystemMerchantSignatureAmountAndOrderBinding() {
        Map<String, Object> payload = signedPayload(MERCHANT, AMOUNT, REFERENCE);
        arrangeBinding(payload);
        when(attemptRepository.findByProviderEventForUpdate(
                "SIMULATOR", PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR, "platform-event"))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(50L)).thenReturn(List.of());
        when(transactionRepository.findByIdempotencyIdentity(anyString())).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(PlatformFinancialTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationService.applyPaidOrder(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    fixture.order().transitionTo(SubscriptionOrderState.APPLIED,
                            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
                    return new SubscriptionApplicationService.ApplicationResult(
                            fixture.order().getPublicId(), fixture.order().getStatus(), invocation.getArgument(1),
                            "contract-public", 20L, SubscriptionEntitlement.Status.ACTIVE,
                            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusYears(1), false,
                            SubscriptionHistory.ActionType.PURCHASED, false);
                });

        PlatformPaymentCallbackService.CallbackResult result = service.process(command(payload));

        assertTrue(result.accepted());
        assertEquals(SubscriptionOrderState.APPLIED, result.orderStatus());
        ArgumentCaptor<PlatformFinancialTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PlatformFinancialTransaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
        assertEquals(PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                transactionCaptor.getValue().getTransactionType());
        assertEquals(0, AMOUNT.compareTo(transactionCaptor.getValue().getAmount()));
    }

    @Test
    void rejectsAnInvalidPlatformSignatureWithoutMutation() {
        Map<String, Object> payload = signedPayload(MERCHANT, AMOUNT, REFERENCE);
        arrangeBinding(payload);

        PlatformPaymentCallbackService.CallbackResult result = service.process(
                new PlatformPaymentCallbackService.CallbackCommand(
                        "SIMULATOR", "invalid-signature", payload, NOW, "invalid-signature"));

        assertRejectedWithoutMutation(result, FinancialErrorCode.CALLBACK_SIGNATURE_INVALID);
    }

    @Test
    void rejectsAValidSignatureForTheWrongSystemMerchantWithoutMutation() {
        Map<String, Object> payload = signedPayload("OTHER_PLATFORM", AMOUNT, REFERENCE);
        arrangeBinding(payload);

        PlatformPaymentCallbackService.CallbackResult result = service.process(command(payload));

        assertRejectedWithoutMutation(result, FinancialErrorCode.CALLBACK_MERCHANT_MISMATCH);
    }

    @Test
    void rejectsAValidSignatureForTheWrongAmountOrOrderReferenceWithoutMutation() {
        Map<String, Object> wrongAmount = signedPayload(MERCHANT, AMOUNT.add(BigDecimal.ONE), REFERENCE);
        arrangeBinding(wrongAmount);
        PlatformPaymentCallbackService.CallbackResult amountResult = service.process(command(wrongAmount));
        assertRejectedWithoutMutation(amountResult, FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH);

        org.mockito.Mockito.reset(
                attemptRepository, transactionRepository, orderRepository, configurationService,
                auditService, applicationService, renewalService, upgradeService);
        Map<String, Object> wrongReference = signedPayload(MERCHANT, AMOUNT, "other-order-ref");
        arrangeBinding(wrongReference);
        PlatformPaymentCallbackService.CallbackResult referenceResult = service.process(command(wrongReference));
        assertRejectedWithoutMutation(referenceResult, FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
    }

    private void arrangeBinding(Map<String, Object> payload) {
        when(attemptRepository.findByProviderAndReferenceForUpdate(
                "SIMULATOR", payload.get("reference").toString()))
                .thenReturn(Optional.of(fixture.attempt()));
        when(orderRepository.findByPublicIdForUpdate("order-public"))
                .thenReturn(Optional.of(fixture.order()));
        PlatformMerchantCredentialResolver.ResolvedMerchantCredentials credentials =
                new PlatformMerchantCredentialResolver.ResolvedMerchantCredentials(
                        MERCHANT, Map.of("signingSecret", SECRET), URI.create("https://simulator.example.test"));
        when(configurationService.requireReady("SIMULATOR"))
                .thenReturn(new PlatformPaymentConfigurationService.ReadyConfiguration(
                        fixture.configuration(), credentials,
                        new PaymentEnvironmentGuard.Readiness(
                                true, PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR,
                                "SIMULATOR", "****FORM", List.of())));
    }

    private PlatformPaymentCallbackService.CallbackCommand command(Map<String, Object> payload) {
        return new PlatformPaymentCallbackService.CallbackCommand(
                "SIMULATOR", payload.get("signature").toString(), payload, NOW, "platform-contract");
    }

    private void assertRejectedWithoutMutation(
            PlatformPaymentCallbackService.CallbackResult result,
            FinancialErrorCode errorCode) {
        assertFalse(result.accepted());
        assertEquals(errorCode, result.errorCode());
        assertEquals(PlatformPaymentAttempt.Status.PENDING, fixture.attempt().getStatus());
        assertEquals(SubscriptionOrderState.PENDING_PAYMENT, fixture.order().getStatus());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).save(any());
    }

    private Map<String, Object> signedPayload(String merchant, BigDecimal amount, String reference) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", merchant);
        payload.put("eventId", "platform-event");
        payload.put("transactionId", "platform-transaction");
        payload.put("reference", reference);
        payload.put("amount", amount);
        payload.put("currency", "VND");
        payload.put("occurredAt", NOW.toString());
        payload.put("status", "SUCCEEDED");
        payload.put("signature", hmac(canonical(payload)));
        return Map.copyOf(payload);
    }

    private String canonical(Map<String, ?> payload) {
        List<String> names = new ArrayList<>(payload.keySet());
        names.removeIf(name -> "signature".equals(name));
        names.sort(String::compareTo);
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (!result.isEmpty()) result.append('&');
            result.append(URLEncoder.encode(name, StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(payload.get(name).toString(), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign platform provider fixture.", exception);
        }
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
        plan.setPrice(AMOUNT);
        plan.setStatus("ACTIVE");
        SubscriptionOrder order = SubscriptionOrder.create(
                "order-public", "SUB-20260801-PROVIDER", owner, hotel,
                SubscriptionOrder.Operation.PURCHASE, plan, "PLAN-30-V1", "PRO", "Professional",
                VndMoney.of(AMOUNT), "YEARLY", 1, SubscriptionOrder.DurationUnit.YEAR,
                "{\"features\":[]}", "order-key", "order-hash",
                LocalDateTime.ofInstant(NOW.plusSeconds(1800), ZoneOffset.UTC));
        order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT,
                LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        ReflectionTestUtils.setField(order, "id", 40L);
        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "SIMULATOR", PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR);
        configuration.configure(true, "****FORM", "env:PLATFORM_SIMULATOR", null, null, null);
        ReflectionTestUtils.setField(configuration, "id", 41L);
        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                "attempt-public", order, configuration, "SIMULATOR", order.priceMoney(),
                "attempt-key", "attempt-hash", order.getExpiresAt());
        attempt.markPending(REFERENCE);
        ReflectionTestUtils.setField(attempt, "id", 50L);
        return new Fixture(order, configuration, attempt);
    }

    private record Fixture(
            SubscriptionOrder order,
            PlatformPaymentConfiguration configuration,
            PlatformPaymentAttempt attempt) {
    }
}

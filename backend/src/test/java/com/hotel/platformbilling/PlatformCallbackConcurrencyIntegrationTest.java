package com.hotel.platformbilling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.adapters.SimulatorPaymentProviderAdapter;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.platformbilling.config.PlatformMerchantCredentialResolver;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationRepository;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptRepository;
import com.hotel.platformbilling.payment.PlatformPaymentCallbackService;
import com.hotel.platformbilling.subscription.PlatformSoftwareContractRepository;
import com.hotel.platformbilling.subscription.PlatformSubscriptionHistoryRepository;
import com.hotel.platformbilling.subscription.SubscriptionApplicationService;
import com.hotel.platformbilling.subscription.SubscriptionEntitlementRepository;
import com.hotel.platformbilling.subscription.SubscriptionRenewalService;
import com.hotel.platformbilling.subscription.SubscriptionUpgradeService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = PlatformCallbackConcurrencyIntegrationTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-callback-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformCallbackConcurrencyIntegrationTest {

    private static final String MERCHANT = "LUXESTAY-PLATFORM-SIMULATOR";
    private static final String SECRET = "platform-simulator-signing-secret-32-bytes";

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    @Import({
            PlatformPaymentCallbackService.class,
            SubscriptionApplicationService.class,
            FinancialAuditService.class,
            SimulatorPaymentProviderAdapter.class,
            PaymentProviderAdapterRegistry.class
    })
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private PlatformPaymentAttemptRepository attemptRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformFinancialTransactionRepository transactionRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformSubscriptionOrderRepository orderRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformSoftwareContractRepository contractRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private SubscriptionEntitlementRepository entitlementRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformSubscriptionHistoryRepository historyRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformPaymentConfigurationRepository configurationRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private HotelRepository hotelRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private SubscriptionPlanRepository planRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformPaymentCallbackService callbackService;
    @org.springframework.beans.factory.annotation.Autowired
    private EntityManager entityManager;

    @MockBean private PlatformPaymentConfigurationService configurationService;
    @MockBean private SubscriptionRenewalService renewalService;
    @MockBean private SubscriptionUpgradeService upgradeService;

    private ExecutorService executor;

    @BeforeEach
    void setUpExecutor() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void sequentialReplayCreatesOneLedgerContractEntitlementAndHistoryEffect() {
        Fixture fixture = createFixture("sequential");
        Map<String, Object> payload = signedPayload(
                fixture.providerReference(), "EVENT-SEQUENTIAL", "TX-SEQUENTIAL");

        PlatformPaymentCallbackService.CallbackResult first = callbackService.process(
                command(payload, "sequential-1"));
        PlatformPaymentCallbackService.CallbackResult replay = callbackService.process(
                command(payload, "sequential-2"));

        assertTrue(first.accepted());
        assertTrue(replay.accepted());
        assertTrue(replay.replayed());
        assertExactlyOneEffect(fixture);
    }

    @Test
    void concurrentEquivalentCallbacksCreateExactlyOneSubscriptionEffect() throws Exception {
        Fixture fixture = createFixture("concurrent");
        Map<String, Object> payload = signedPayload(
                fixture.providerReference(), "EVENT-CONCURRENT", "TX-CONCURRENT");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<PlatformPaymentCallbackService.CallbackResult> invoke = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Platform callback start barrier timed out.");
            }
            return callbackService.process(command(payload, "concurrent-platform-callback"));
        };

        Future<PlatformPaymentCallbackService.CallbackResult> first = executor.submit(invoke);
        Future<PlatformPaymentCallbackService.CallbackResult> second = executor.submit(invoke);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        List<PlatformPaymentCallbackService.CallbackResult> results = List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS));

        assertTrue(results.stream().allMatch(PlatformPaymentCallbackService.CallbackResult::accepted));
        assertEquals(1, results.stream().filter(PlatformPaymentCallbackService.CallbackResult::replayed).count());
        assertExactlyOneEffect(fixture);
    }

    private Fixture createFixture(String suffix) {
        String unique = suffix + '-' + UUID.randomUUID();
        User owner = new User();
        owner.setUsername("platform-callback-" + unique);
        owner.setEmail("platform-callback-" + unique + "@example.test");
        owner.setPasswordHash("test");
        owner.setStatus("ACTIVE");
        owner = userRepository.saveAndFlush(owner);

        Hotel hotel = new Hotel();
        hotel.setCode("PLATFORM-CALLBACK-" + unique);
        hotel.setSlug("platform-callback-" + unique);
        hotel.setName("Platform Callback Hotel " + unique);
        hotel.setAddressLine("Platform Callback Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel = hotelRepository.saveAndFlush(hotel);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("PRO-" + unique);
        plan.setNameVi("Professional");
        plan.setNameEn("Professional");
        plan.setBillingType("YEARLY");
        plan.setPrice(BigDecimal.valueOf(2_400_000));
        plan.setIsLifetime(false);
        plan.setStatus("ACTIVE");
        plan = planRepository.saveAndFlush(plan);

        PlatformPaymentConfiguration configuration = configurationRepository
                .findByProviderAndEnvironment("SIMULATOR", PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR)
                .orElseGet(() -> {
                    PlatformPaymentConfiguration created = PlatformPaymentConfiguration.create(
                            "SIMULATOR", PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR);
                    created.configure(true, "****ATOR", "env:PLATFORM_SIMULATOR", null, null, null);
                    return configurationRepository.saveAndFlush(created);
                });

        SubscriptionOrder order = SubscriptionOrder.create(
                "order-" + unique, "SUB-" + unique, owner, hotel,
                SubscriptionOrder.Operation.PURCHASE, plan, "PLAN-" + plan.getId() + "-V1",
                plan.getCode(), plan.getNameVi(), VndMoney.of(plan.getPrice()), "YEARLY", 1,
                SubscriptionOrder.DurationUnit.YEAR, "{\"features\":[]}",
                "order-key-" + unique, "order-hash-" + unique, LocalDateTime.now().plusHours(1));
        order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, LocalDateTime.now());
        order = orderRepository.saveAndFlush(order);

        String providerReference = "platform-attempt-" + unique;
        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                "attempt-" + unique, order, configuration, "SIMULATOR", order.priceMoney(),
                "attempt-key-" + unique, "attempt-hash-" + unique, order.getExpiresAt());
        attempt.markPending(providerReference);
        attempt = attemptRepository.saveAndFlush(attempt);

        PlatformMerchantCredentialResolver.ResolvedMerchantCredentials credentials =
                new PlatformMerchantCredentialResolver.ResolvedMerchantCredentials(
                        MERCHANT, Map.of("signingSecret", SECRET), null);
        when(configurationService.requireReady("SIMULATOR"))
                .thenReturn(new PlatformPaymentConfigurationService.ReadyConfiguration(
                        configuration, credentials,
                        new PaymentEnvironmentGuard.Readiness(
                                true, PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR,
                                "SIMULATOR", "****ATOR", List.of())));
        return new Fixture(order.getPublicId(), attempt.getPublicId(), attempt.getId(), hotel.getId(), providerReference);
    }

    private void assertExactlyOneEffect(Fixture fixture) {
        entityManager.clear();
        PlatformPaymentAttempt attempt = attemptRepository.findByPublicId(fixture.attemptPublicId()).orElseThrow();
        SubscriptionOrder order = orderRepository.findByPublicId(fixture.orderPublicId()).orElseThrow();
        assertEquals(PlatformPaymentAttempt.Status.SUCCESS, attempt.getStatus());
        assertEquals(SubscriptionOrderState.APPLIED, order.getStatus());
        assertEquals(1, transactionRepository.findByAttemptIdOrderByOccurredAtAsc(fixture.attemptId()).size());
        assertTrue(contractRepository.findByOrderId(order.getId()).isPresent());
        assertTrue(entitlementRepository.findByTargetHotelId(fixture.hotelId()).isPresent());
        assertEquals(1, historyRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()).size());
    }

    private PlatformPaymentCallbackService.CallbackCommand command(
            Map<String, Object> payload,
            String correlationId) {
        return new PlatformPaymentCallbackService.CallbackCommand(
                "SIMULATOR", payload.get("signature").toString(), payload, Instant.now(), correlationId);
    }

    private Map<String, Object> signedPayload(String reference, String eventId, String transactionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", MERCHANT);
        payload.put("eventId", eventId);
        payload.put("transactionId", transactionId);
        payload.put("reference", reference);
        payload.put("amount", 2_400_000);
        payload.put("currency", "VND");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("status", "SUCCEEDED");
        payload.put("signature", hmac(canonical(payload)));
        return Map.copyOf(payload);
    }

    private String canonical(Map<String, Object> payload) {
        List<String> names = new ArrayList<>(payload.keySet());
        names.remove("signature");
        names.sort(String::compareTo);
        return names.stream()
                .map(name -> java.net.URLEncoder.encode(name, StandardCharsets.UTF_8)
                        + "=" + java.net.URLEncoder.encode(payload.get(name).toString(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign platform integration fixture.", exception);
        }
    }

    private record Fixture(
            String orderPublicId,
            String attemptPublicId,
            Long attemptId,
            Long hotelId,
            String providerReference) {
    }
}

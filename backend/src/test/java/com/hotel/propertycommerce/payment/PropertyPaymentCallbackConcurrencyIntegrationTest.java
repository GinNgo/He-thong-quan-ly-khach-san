package com.hotel.propertycommerce.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.adapters.SimulatorPaymentProviderAdapter;
import com.hotel.paymentprovider.audit.FinancialAuditEventRepository;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import java.time.LocalDate;
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

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = PropertyPaymentCallbackConcurrencyIntegrationTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:property-callback-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyPaymentCallbackConcurrencyIntegrationTest {

    private static final String SECRET = "simulator-signing-secret-with-32-chars";

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    @Import({
            PropertyPaymentCallbackService.class,
            FinancialAuditService.class,
            SimulatorPaymentProviderAdapter.class,
            PaymentProviderAdapterRegistry.class
    })
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private PropertyPaymentAttemptRepository attemptRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyFinancialTransactionRepository transactionRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private FinancialAuditEventRepository auditRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private HotelRepository hotelRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private ReservationRepository reservationRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyPaymentCallbackService callbackService;
    @org.springframework.beans.factory.annotation.Autowired
    private EntityManager entityManager;

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
    void sequentialEquivalentReplayCreatesOneLedgerEffect() {
        Fixture fixture = createFixture("sequential");
        Map<String, Object> payload = signedPayload(fixture.attemptPublicId(), "EVENT-SEQUENTIAL", "TX-SEQUENTIAL");

        PropertyPaymentCallbackService.CallbackResult first = callbackService.process(command(payload, "sequential-1"));
        PropertyPaymentCallbackService.CallbackResult replay = callbackService.process(command(payload, "sequential-2"));

        assertTrue(first.accepted());
        assertTrue(replay.accepted());
        assertTrue(replay.replayed());
        entityManager.clear();
        PropertyPaymentAttempt attempt = attemptRepository.findByPublicId(fixture.attemptPublicId()).orElseThrow();
        assertEquals(PaymentState.SUCCESS, attempt.getStatus());
        assertEquals(1, transactionRepository.findByAttemptIdOrderByOccurredAtAsc(attempt.getId()).size());
        assertEquals(2, auditRepository.findAll().stream()
                .filter(event -> fixture.attemptPublicId().equals(event.getAggregateId()))
                .count());
    }

    @Test
    void concurrentEquivalentCallbacksCreateOneLedgerEffectAndOneReplay() throws Exception {
        Fixture fixture = createFixture("concurrent");
        Map<String, Object> payload = signedPayload(fixture.attemptPublicId(), "EVENT-CONCURRENT", "TX-CONCURRENT");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<PropertyPaymentCallbackService.CallbackResult> invoke = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Callback start barrier timed out.");
            }
            return callbackService.process(command(payload, "concurrent-callback"));
        };

        Future<PropertyPaymentCallbackService.CallbackResult> first = executor.submit(invoke);
        Future<PropertyPaymentCallbackService.CallbackResult> second = executor.submit(invoke);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        List<PropertyPaymentCallbackService.CallbackResult> results = List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS));

        assertTrue(results.stream().allMatch(PropertyPaymentCallbackService.CallbackResult::accepted));
        assertEquals(1, results.stream().filter(PropertyPaymentCallbackService.CallbackResult::replayed).count());
        entityManager.clear();
        PropertyPaymentAttempt attempt = attemptRepository.findByPublicId(fixture.attemptPublicId()).orElseThrow();
        assertEquals(PaymentState.SUCCESS, attempt.getStatus());
        assertEquals(1, transactionRepository.findByAttemptIdOrderByOccurredAtAsc(attempt.getId()).size());
        assertEquals(2, auditRepository.findAll().stream()
                .filter(event -> fixture.attemptPublicId().equals(event.getAggregateId()))
                .count());
    }

    private Fixture createFixture(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID();
        User user = new User();
        user.setUsername("callback-" + unique);
        user.setEmail("callback-" + unique + "@example.test");
        user.setPasswordHash("test");
        user.setStatus("ACTIVE");
        user = userRepository.saveAndFlush(user);

        Hotel hotel = new Hotel();
        hotel.setCode("CALLBACK-" + unique);
        hotel.setSlug("callback-" + unique);
        hotel.setName("Callback Hotel " + unique);
        hotel.setAddressLine("Callback Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel = hotelRepository.saveAndFlush(hotel);

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2028, 5, 1));
        reservation.setCheckOutDate(LocalDate.of(2028, 5, 2));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(350_000));
        reservation.setStatus("PENDING_PAYMENT");
        reservation = reservationRepository.saveAndFlush(reservation);

        String publicId = "attempt-" + unique;
        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                publicId,
                hotel,
                reservation,
                null,
                user,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MOMO",
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                VndMoney.of(350_000),
                null,
                "{}",
                "idempotency-" + unique,
                "hash-" + unique,
                LocalDateTime.now().plusHours(1));
        attempt.bindProviderOrderReference(publicId);
        attempt.transitionTo(PaymentState.PENDING, LocalDateTime.now(), null, null);
        attempt = attemptRepository.saveAndFlush(attempt);
        return new Fixture(attempt.getPublicId(), reservation.getId());
    }

    private PropertyPaymentCallbackService.CallbackCommand command(
            Map<String, Object> payload,
            String correlationId) {
        return new PropertyPaymentCallbackService.CallbackCommand(
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                "PROPERTY-SIMULATOR",
                payload.get("signature").toString(),
                payload,
                Map.of("signingSecret", SECRET),
                Instant.now(),
                correlationId);
    }

    private Map<String, Object> signedPayload(
            String reference,
            String eventId,
            String transactionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", "PROPERTY-SIMULATOR");
        payload.put("eventId", eventId);
        payload.put("transactionId", transactionId);
        payload.put("reference", reference);
        payload.put("amount", 350000);
        payload.put("currency", "VND");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("status", "SUCCEEDED");
        payload.put("signature", hmac(canonical(payload)));
        return payload;
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
            throw new IllegalStateException("Unable to sign integration fixture.", exception);
        }
    }

    private record Fixture(String attemptPublicId, Long reservationId) {
    }
}

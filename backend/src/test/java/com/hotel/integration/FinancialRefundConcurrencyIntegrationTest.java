package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.propertycommerce.refund.PropertyRefundRequest;
import com.hotel.propertycommerce.refund.PropertyRefundRequestRepository;
import com.hotel.propertycommerce.refund.PropertyRefundService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.PropertyAccessService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = FinancialRefundConcurrencyIntegrationTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:financial-refund-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinancialRefundConcurrencyIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    @Import(FinancialAuditService.class)
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        PropertyRefundService propertyRefundService(
                PropertyFinancialTransactionRepository transactionRepository,
                PropertyRefundRequestRepository requestRepository,
                PropertyAccessService propertyAccessService,
                FinancialAuditService auditService) {
            return new PropertyRefundService(transactionRepository, requestRepository, propertyAccessService, auditService);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private HotelRepository hotelRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private ReservationRepository reservationRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyFinancialTransactionRepository transactionRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyRefundRequestRepository refundRequestRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyRefundService refundService;
    @org.springframework.beans.factory.annotation.Autowired
    private EntityManager entityManager;

    @MockBean
    private PropertyAccessService propertyAccessService;

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
    void concurrentPartialRequestsCannotReserveMoreThanOriginalDebit() throws Exception {
        Fixture fixture = createFixture("concurrent", 1_000_000);
        stubAccess(fixture);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<CallResult> invokeFirst = concurrentRequest(fixture, 600_000, "concurrent-a", ready, start);
        Callable<CallResult> invokeSecond = concurrentRequest(fixture, 500_000, "concurrent-b", ready, start);
        Future<CallResult> first = executor.submit(invokeFirst);
        Future<CallResult> second = executor.submit(invokeSecond);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        List<CallResult> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        assertEquals(1, results.stream().filter(CallResult::accepted).count());
        assertEquals(1, results.stream().filter(result -> result.error() == FinancialErrorCode.REFUND_EXCEEDS_BALANCE).count());

        PropertyRefundService.RefundResult accepted = results.stream()
                .filter(CallResult::accepted)
                .map(CallResult::result)
                .findFirst()
                .orElseThrow();
        refundService.approve(accepted.publicId(), "concurrent-approve");
        refundService.completeSucceeded(accepted.publicId(), "provider-concurrent", "concurrent-complete");

        entityManager.clear();
        PropertyFinancialTransaction original = transactionRepository.findByPublicId(fixture.originalPublicId()).orElseThrow();
        List<PropertyFinancialTransaction> effects = transactionRepository
                .findByOriginalTransactionIdOrderByOccurredAtAsc(original.getId());
        assertEquals(1_000_000, original.getAmount().intValueExact());
        assertEquals(1, effects.size());
        assertEquals(accepted.requestedAmount().intValueExact(), effects.getFirst().getAmount().intValueExact());
        assertTrue(effects.stream().map(PropertyFinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(original.getAmount()) <= 0);
    }

    @Test
    void replayedRequestIsIdempotentAndCreatesNoDuplicateRequest() {
        Fixture fixture = createFixture("replay", 1_000_000);
        stubAccess(fixture);
        PropertyRefundService.RequestCommand command = command(fixture, 250_000, "replay-key", "replay-reason");

        PropertyRefundService.RefundResult first = refundService.request(command);
        PropertyRefundService.RefundResult replay = refundService.request(command);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.publicId(), replay.publicId());
        assertEquals(1, refundRequestRepository.findByOriginalTransactionIdOrderByRequestedAtAsc(
                fixture.originalId()).size());
        assertEquals(0, transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(
                fixture.originalId()).size());
    }

    @Test
    void excessiveCumulativeRefundIsRejectedAfterSuccessfulRefundEffect() {
        Fixture fixture = createFixture("excessive", 1_000_000);
        stubAccess(fixture);

        PropertyRefundService.RefundResult first = refundService.request(
                command(fixture, 700_000, "excessive-first", "first partial refund"));
        refundService.approve(first.publicId(), "excessive-approve");
        refundService.completeSucceeded(first.publicId(), "provider-excessive-first", "excessive-complete");

        FinancialException excessive = assertThrows(FinancialException.class, () -> refundService.request(
                command(fixture, 400_000, "excessive-second", "exceeds remaining balance")));
        assertEquals(FinancialErrorCode.REFUND_EXCEEDS_BALANCE, excessive.code());

        PropertyRefundService.RefundResult exact = refundService.request(
                command(fixture, 300_000, "excessive-exact", "refund remaining balance"));
        assertEquals(300_000, exact.requestedAmount().intValueExact());

        entityManager.clear();
        PropertyFinancialTransaction original = transactionRepository.findByPublicId(fixture.originalPublicId()).orElseThrow();
        BigDecimal refunded = transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(original.getId()).stream()
                .map(PropertyFinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(1_000_000, original.getAmount().intValueExact());
        assertEquals(700_000, refunded.intValueExact());
        assertTrue(refunded.compareTo(original.getAmount()) <= 0);
        assertNotNull(exact.publicId());
    }

    private Callable<CallResult> concurrentRequest(
            Fixture fixture,
            int amount,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Refund request start barrier timed out.");
            }
            try {
                return CallResult.accepted(refundService.request(
                        command(fixture, amount, idempotencyKey, "concurrent partial refund")));
            } catch (FinancialException exception) {
                return CallResult.rejected(exception.code());
            }
        };
    }

    private PropertyRefundService.RequestCommand command(
            Fixture fixture,
            int amount,
            String idempotencyKey,
            String reason) {
        return new PropertyRefundService.RequestCommand(
                fixture.originalPublicId(), BigDecimal.valueOf(amount), reason, idempotencyKey, idempotencyKey);
    }

    private void stubAccess(Fixture fixture) {
        when(propertyAccessService.currentUser()).thenReturn(fixture.actor());
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(fixture.hotel().getId()));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
    }

    private Fixture createFixture(String suffix, int originalAmount) {
        String unique = suffix + "-" + UUID.randomUUID();
        User actor = new User();
        actor.setUsername("refund-" + unique);
        actor.setEmail("refund-" + unique + "@example.test");
        actor.setPasswordHash("test");
        actor.setFullName("Refund Test User");
        actor.setStatus("ACTIVE");
        actor = userRepository.saveAndFlush(actor);

        Hotel hotel = new Hotel();
        hotel.setCode("REFUND-" + unique);
        hotel.setSlug("refund-" + unique);
        hotel.setName("Refund Test Hotel " + unique);
        hotel.setAddressLine("Refund Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel = hotelRepository.saveAndFlush(hotel);

        Reservation reservation = new Reservation();
        reservation.setUser(actor);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2028, 5, 1));
        reservation.setCheckOutDate(LocalDate.of(2028, 5, 2));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(originalAmount));
        reservation.setStatus("CONFIRMED");
        reservation = reservationRepository.saveAndFlush(reservation);

        PropertyFinancialTransaction original = PropertyFinancialTransaction.record(
                "property-tx-" + UUID.randomUUID(),
                hotel,
                reservation,
                null,
                null,
                null,
                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(originalAmount),
                "MOMO",
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                "provider-charge-" + unique,
                "payment-effect-" + unique,
                "PROVIDER",
                null,
                "Booking payment",
                LocalDateTime.now());
        original = transactionRepository.saveAndFlush(original);
        return new Fixture(actor, hotel, reservation, original.getId(), original.getPublicId());
    }

    private record Fixture(
            User actor,
            Hotel hotel,
            Reservation reservation,
            Long originalId,
            String originalPublicId) {
    }

    private record CallResult(
            boolean accepted,
            PropertyRefundService.RefundResult result,
            FinancialErrorCode error) {

        static CallResult accepted(PropertyRefundService.RefundResult result) {
            return new CallResult(true, result, null);
        }

        static CallResult rejected(FinancialErrorCode error) {
            return new CallResult(false, null, error);
        }
    }
}

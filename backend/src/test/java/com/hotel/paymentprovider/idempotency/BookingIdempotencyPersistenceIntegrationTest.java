package com.hotel.paymentprovider.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
@Import({FinancialIdempotencyService.class, BookingIdempotencyPersistenceIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingIdempotencyPersistenceIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private FinancialIdempotencyService service;

    @org.springframework.beans.factory.annotation.Autowired
    private FinancialIdempotencyRepository repository;

    @BeforeEach
    @AfterEach
    void clearLedger() {
        repository.deleteAll();
    }

    @Test
    void completedBookingReplaySurvivesAServiceRecreationAndRejectsConflictingPayload() {
        FinancialIdempotencyService.BeginCommand command = command(Map.of(
                "roomTypeId", 7,
                "checkInDate", "2026-08-10",
                "checkOutDate", "2026-08-12"));

        FinancialIdempotencyService.BeginResult acquired = service.begin(command);
        service.complete(acquired.recordId(), 201, "{\"id\":77}");

        FinancialIdempotencyService recreated = new FinancialIdempotencyService(
                repository, new ObjectMapper().findAndRegisterModules());
        FinancialIdempotencyService.Replay replay = assertInstanceOf(
                FinancialIdempotencyService.Replay.class, recreated.begin(command));
        assertEquals("{\"id\":77}", replay.responseBody());

        FinancialException conflict = assertThrows(FinancialException.class, () -> recreated.begin(
                command(Map.of(
                        "roomTypeId", 8,
                        "checkInDate", "2026-08-10",
                        "checkOutDate", "2026-08-12"))));
        assertEquals(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED, conflict.code());
    }

    @Test
    void concurrentDoubleSubmitAcquiresOnlyOneBookingIdentity() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> beginAfter(start));
            var second = executor.submit(() -> beginAfter(start));
            start.countDown();

            FinancialIdempotencyService.BeginResult firstResult = first.get(10, TimeUnit.SECONDS);
            FinancialIdempotencyService.BeginResult secondResult = second.get(10, TimeUnit.SECONDS);
            long acquiredCount = java.util.stream.Stream.of(firstResult, secondResult)
                    .filter(FinancialIdempotencyService.Acquired.class::isInstance)
                    .count();

            assertEquals(1L, acquiredCount);
            assertEquals(1L, repository.count());
        }
    }

    private FinancialIdempotencyService.BeginResult beginAfter(CountDownLatch start) {
        try {
            start.await(5, TimeUnit.SECONDS);
            return service.begin(command(Map.of("roomTypeId", 7)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private FinancialIdempotencyService.BeginCommand command(Object payload) {
        return new FinancialIdempotencyService.BeginCommand(
                "PROPERTY_COMMERCE",
                "RESERVATION_CREATE",
                "customer@example.test",
                "booking-key",
                payload,
                null,
                null,
                "corr-booking");
    }
}

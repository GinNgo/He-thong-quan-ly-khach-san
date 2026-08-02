package com.hotel.paymentprovider.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialIdempotencyServiceTest {

    @Test
    void samePayloadReplaysAndDifferentPayloadIsRejected() {
        FinancialIdempotencyRepository repository = mock(FinancialIdempotencyRepository.class);
        ConcurrentHashMap<String, FinancialIdempotencyRecord> records = new ConcurrentHashMap<>();
        stubStore(repository, records);
        FinancialIdempotencyService service = new FinancialIdempotencyService(repository, new ObjectMapper());
        FinancialIdempotencyService.BeginCommand command = command(Map.of("amount", 100, "currency", "VND"));

        FinancialIdempotencyService.BeginResult first = service.begin(command);
        assertInstanceOf(FinancialIdempotencyService.Acquired.class, first);
        FinancialIdempotencyRecord stored = records.values().iterator().next();
        stored.complete(200, "{\"ok\":true}", java.time.LocalDateTime.now());

        FinancialIdempotencyService.BeginResult replay = service.begin(command);
        assertInstanceOf(FinancialIdempotencyService.Replay.class, replay);
        assertEquals("{\"ok\":true}", ((FinancialIdempotencyService.Replay) replay).responseBody());

        FinancialException conflict = assertThrows(FinancialException.class,
                () -> service.begin(commandWithPayload(Map.of("amount", 101, "currency", "VND"))));
        assertEquals(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED, conflict.code());
    }

    @Test
    void concurrentClaimsProduceOneAcquisitionAndOneReplayOrInProgress() throws Exception {
        FinancialIdempotencyRepository repository = mock(FinancialIdempotencyRepository.class);
        ConcurrentHashMap<String, FinancialIdempotencyRecord> records = new ConcurrentHashMap<>();
        AtomicInteger saves = new AtomicInteger();
        stubStore(repository, records, saves);
        FinancialIdempotencyService service = new FinancialIdempotencyService(repository, new ObjectMapper());
        CountDownLatch start = new CountDownLatch(1);
        FinancialIdempotencyService.BeginResult[] results = new FinancialIdempotencyService.BeginResult[2];
        Thread first = new Thread(() -> results[0] = claimAfter(start, service));
        Thread second = new Thread(() -> results[1] = claimAfter(start, service));
        first.start();
        second.start();
        start.countDown();
        first.join(TimeUnit.SECONDS.toMillis(5));
        second.join(TimeUnit.SECONDS.toMillis(5));

        assertEquals(1, saves.get());
        assertEquals(1, java.util.Arrays.stream(results)
                .filter(result -> result instanceof FinancialIdempotencyService.Acquired).count());
    }

    @Test
    void reservationClaimCanResolvePropertyOwnershipInsideTheLockedMutation() {
        FinancialIdempotencyRepository repository = mock(FinancialIdempotencyRepository.class);
        ConcurrentHashMap<String, FinancialIdempotencyRecord> records = new ConcurrentHashMap<>();
        stubStore(repository, records);
        FinancialIdempotencyService service = new FinancialIdempotencyService(repository, new ObjectMapper());

        FinancialIdempotencyService.BeginResult result = service.begin(
                new FinancialIdempotencyService.BeginCommand(
                        "PROPERTY_COMMERCE",
                        "RESERVATION_CREATE",
                        "customer@example.test",
                        "booking-key",
                        Map.of("roomTypeId", 7),
                        null,
                        null,
                        "corr"));

        assertInstanceOf(FinancialIdempotencyService.Acquired.class, result);
    }

    private FinancialIdempotencyService.BeginResult claimAfter(CountDownLatch start, FinancialIdempotencyService service) {
        try {
            start.await(5, TimeUnit.SECONDS);
            return service.begin(command(Map.of("amount", 100)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private FinancialIdempotencyService.BeginCommand command(Object payload) {
        return new FinancialIdempotencyService.BeginCommand(
                "PROPERTY_COMMERCE", "PAYMENT", "reservation-1", "same-key", payload, 7L, 9L, "corr");
    }

    private FinancialIdempotencyService.BeginCommand commandWithPayload(Object payload) {
        return new FinancialIdempotencyService.BeginCommand(
                "PROPERTY_COMMERCE", "PAYMENT", "reservation-1", "same-key", payload, 7L, 9L, "corr");
    }

    private void stubStore(FinancialIdempotencyRepository repository,
                           ConcurrentHashMap<String, FinancialIdempotencyRecord> records) {
        stubStore(repository, records, new AtomicInteger());
    }

    private void stubStore(FinancialIdempotencyRepository repository,
                           ConcurrentHashMap<String, FinancialIdempotencyRecord> records,
                           AtomicInteger saves) {
        when(repository.findByContextAndOperationAndScopeKeyAndIdempotencyKey(any(), any(), any(), any()))
                .thenAnswer((Answer<Optional<FinancialIdempotencyRecord>>) invocation ->
                        Optional.ofNullable(records.get(identity(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)))));
        when(repository.saveAndFlush(any(FinancialIdempotencyRecord.class))).thenAnswer(invocation -> {
            FinancialIdempotencyRecord record = invocation.getArgument(0);
            String key = identity(record.getContext(), record.getOperation(), record.getScopeKey(), record.getIdempotencyKey());
            ReflectionTestUtils.setField(record, "id", IDS.incrementAndGet());
            if (records.putIfAbsent(key, record) != null) throw new DataIntegrityViolationException("duplicate");
            saves.incrementAndGet();
            return record;
        });
    }

    private String identity(String context, String operation, String scope, String key) {
        return String.join("|", context, operation, scope, key);
    }

    private static final AtomicLong IDS = new AtomicLong();
}

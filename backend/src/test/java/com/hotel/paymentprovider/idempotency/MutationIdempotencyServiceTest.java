package com.hotel.paymentprovider.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MutationIdempotencyServiceTest {

    private FinancialIdempotencyService delegate;
    private MutationIdempotencyService service;

    @BeforeEach
    void setUp() {
        delegate = mock(FinancialIdempotencyService.class);
        service = new MutationIdempotencyService(delegate, new ObjectMapper());
    }

    @Test
    void acquiredRequestRunsOnceAndPersistsResponse() {
        FinancialIdempotencyRecord record = mock(FinancialIdempotencyRecord.class);
        when(record.getId()).thenReturn(17L);
        when(delegate.begin(any())).thenReturn(new FinancialIdempotencyService.Acquired(record));

        AtomicInteger executions = new AtomicInteger();
        SampleResponse response = service.execute(command(), 201, SampleResponse.class,
                () -> new SampleResponse(executions.incrementAndGet(), "created"));

        assertEquals(1, response.id());
        assertEquals(1, executions.get());
        verify(delegate).complete(17L, 201, "{\"id\":1,\"status\":\"created\"}");
    }

    @Test
    void completedRequestReplaysWithoutRunningMutation() {
        when(delegate.begin(any())).thenReturn(new FinancialIdempotencyService.Replay(
                17L, 201, "{\"id\":42,\"status\":\"created\"}"));
        AtomicInteger executions = new AtomicInteger();

        SampleResponse response = service.execute(command(), 201, SampleResponse.class, () -> {
            executions.incrementAndGet();
            return new SampleResponse(99, "duplicate");
        });

        assertEquals(new SampleResponse(42, "created"), response);
        assertEquals(0, executions.get());
    }

    @Test
    void inProgressDuplicateIsRejectedAsRetryableConflict() {
        when(delegate.begin(any())).thenReturn(new FinancialIdempotencyService.InProgress(17L, "corr-original"));

        FinancialException exception = assertThrows(FinancialException.class,
                () -> service.execute(command(), 201, SampleResponse.class,
                        () -> new SampleResponse(1, "created")));

        assertEquals(FinancialErrorCode.CONCURRENT_MODIFICATION, exception.code());
    }

    @Test
    void inProgressRequestRecoversACommittedBusinessResultWithoutRunningMutationAgain() {
        when(delegate.begin(any())).thenReturn(new FinancialIdempotencyService.InProgress(17L, "corr-original"));
        SampleResponse recovered = new SampleResponse(77, "created");

        SampleResponse response = service.execute(command(), 201, SampleResponse.class,
                () -> new SampleResponse(99, "duplicate"),
                () -> recovered);

        assertEquals(recovered, response);
        verify(delegate).complete(17L, 201, "{\"id\":77,\"status\":\"created\"}");
    }

    @Test
    void failedClaimCanRetryWithTheSameIdentity() {
        when(delegate.begin(any())).thenReturn(new FinancialIdempotencyService.RetryableFailure(17L));

        SampleResponse response = service.execute(command(), 201, SampleResponse.class,
                () -> new SampleResponse(7, "retried"));

        assertEquals(new SampleResponse(7, "retried"), response);
        verify(delegate).complete(17L, 201, "{\"id\":7,\"status\":\"retried\"}");
    }

    @Test
    void failedMutationMarksTheClaimFailedBeforeRethrowing() {
        FinancialIdempotencyRecord record = mock(FinancialIdempotencyRecord.class);
        when(record.getId()).thenReturn(17L);
        when(delegate.begin(any())).thenReturn(new FinancialIdempotencyService.Acquired(record));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.execute(command(), 201, SampleResponse.class,
                        () -> { throw new IllegalStateException("boom"); }));

        assertEquals("boom", failure.getMessage());
        verify(delegate).fail(17L);
    }

    private FinancialIdempotencyService.BeginCommand command() {
        return new FinancialIdempotencyService.BeginCommand(
                "PROPERTY_COMMERCE",
                "RESERVATION_CREATE",
                "customer@example.test",
                "booking-key",
                Map.of("roomTypeId", 7),
                null,
                null,
                "corr-1");
    }

    private record SampleResponse(int id, String status) {
    }
}

package com.hotel.propertyreview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyReviewEmailOutboxServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 9, 0);

    @Mock private PropertyReviewEmailOutboxRepository outboxRepository;
    @Mock private PropertyReviewEmailDeliveryAttemptRepository attemptRepository;

    private PropertyReviewEmailOutboxService service;

    @BeforeEach
    void setUp() {
        service = new PropertyReviewEmailOutboxService(
                outboxRepository,
                attemptRepository,
                Clock.fixed(Instant.parse("2026-08-04T09:00:00Z"), ZoneOffset.UTC),
                3,
                25,
                300,
                30,
                3600);
    }

    @Test
    void enqueueIsIdempotentPerAuditEventAndRecipient() {
        PropertyReviewEmailOutbox existing = item("owner@example.test", 3);
        ReflectionTestUtils.setField(existing, "id", 44L);
        when(outboxRepository.findByAuditEventIdAndRecipientUserId(101L, 7L))
                .thenReturn(Optional.of(existing));

        var result = service.enqueue(
                101L, 51L, 7L, "owner@example.test", "Approved", "Property approved.", NOW);

        assertTrue(result.replayed());
        assertEquals(44L, result.outboxId());
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidRecipientCreatesTerminalEvidenceWithoutThrowing() {
        when(outboxRepository.findByAuditEventIdAndRecipientUserId(101L, 7L))
                .thenReturn(Optional.empty());
        when(outboxRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.enqueue(101L, 51L, 7L, "legacy-invalid", "Approved", "Property approved.", NOW);

        ArgumentCaptor<PropertyReviewEmailOutbox> outbox = ArgumentCaptor.forClass(PropertyReviewEmailOutbox.class);
        verify(outboxRepository).saveAndFlush(outbox.capture());
        assertEquals(PropertyReviewEmailStatus.DEAD_LETTER, outbox.getValue().getStatus());
        assertEquals("RECIPIENT_INVALID", outbox.getValue().getLastErrorCode());
        ArgumentCaptor<PropertyReviewEmailDeliveryAttempt> attempt =
                ArgumentCaptor.forClass(PropertyReviewEmailDeliveryAttempt.class);
        verify(attemptRepository).save(attempt.capture());
        assertEquals("RECIPIENT_INVALID", attempt.getValue().getErrorCode());
    }

    @Test
    void failedDeliveryUsesExponentialRetryAndDurableAttempt() {
        PropertyReviewEmailOutbox item = item("owner@example.test", 3);
        ReflectionTestUtils.setField(item, "id", 45L);
        when(outboxRepository.findDueForUpdate(any(), any(), any(), any(), any()))
                .thenReturn(List.of(item));

        var claim = service.claimDue().getFirst();
        when(outboxRepository.findByIdForUpdate(45L)).thenReturn(Optional.of(item));
        service.complete(claim, false, 12);

        assertEquals(PropertyReviewEmailStatus.FAILED, item.getStatus());
        assertEquals(1, item.getAttemptCount());
        assertEquals(NOW.plusSeconds(30), item.getNextAttemptAt());
        verify(attemptRepository).save(any(PropertyReviewEmailDeliveryAttempt.class));
    }

    @Test
    void staleClaimBecomesFailedAttemptAndMaxAttemptsDeadLetters() {
        PropertyReviewEmailOutbox item = item("owner@example.test", 1);
        ReflectionTestUtils.setField(item, "id", 46L);
        item.claim("stale-token", NOW.minusMinutes(10));
        when(outboxRepository.findDueForUpdate(any(), any(), any(), any(), any()))
                .thenReturn(List.of(item));

        var claims = service.claimDue();

        assertTrue(claims.isEmpty());
        assertEquals(PropertyReviewEmailStatus.DEAD_LETTER, item.getStatus());
        assertEquals("CLAIM_TIMEOUT", item.getLastErrorCode());
        verify(attemptRepository).save(any(PropertyReviewEmailDeliveryAttempt.class));
    }

    private PropertyReviewEmailOutbox item(String email, int maxAttempts) {
        return new PropertyReviewEmailOutbox(
                101L, 51L, 7L, email, "Approved", "Property approved.", maxAttempts, NOW);
    }
}

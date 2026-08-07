package com.hotel.emailoutbox;

import com.hotel.services.OperationalAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.hotel.emailoutbox.EmailOutboxDtos.EnqueueRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOutboxServiceTest {

    @Mock
    private EmailOutboxRepository outboxRepository;
    @Mock
    private EmailDeliveryAttemptRepository attemptRepository;
    @Mock
    private EmailDeliveryPort deliveryPort;
    @Mock
    private OperationalAuditService auditService;

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 4, 10, 0);
    private EmailOutboxService service;

    @BeforeEach
    void setUp() {
        service = new EmailOutboxService(outboxRepository, attemptRepository, deliveryPort, auditService,
                null, Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC), 30, 3600, 2);
    }

    @Test
    void enqueue_IsIdempotentAndRejectsPayloadMismatch() {
        EnqueueRequest request = request("mail-1", 5);
        when(outboxRepository.findByIdempotencyKey("mail-1")).thenReturn(Optional.empty());
        when(outboxRepository.save(any(EmailOutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.enqueue(request);
        assertEquals(false, first.replayed());
        verify(outboxRepository).save(any(EmailOutboxMessage.class));

        var saved = new EmailOutboxMessage(null, "mail-1", firstHash(request), "registration", "v1",
                "guest@example.com", "Subject", null, "body", null, null, null, 5, now);
        when(outboxRepository.findByIdempotencyKey("mail-1")).thenReturn(Optional.of(saved));
        var replay = service.enqueue(request);
        assertEquals(true, replay.replayed());
        verify(outboxRepository).save(any(EmailOutboxMessage.class));

        EnqueueRequest changed = new EnqueueRequest(1L, "mail-1", "registration", "v1",
                "guest@example.com", "Changed", null, "body", null, null, null, 5);
        assertThrows(IllegalArgumentException.class, () -> service.enqueue(changed));
    }

    @Test
    void processOne_RecordsRetryWithExponentialBackoffAndDeadLettersAtLimit() {
        EmailOutboxMessage message = message("retry-1", "hash");
        when(outboxRepository.findForUpdate(1L)).thenReturn(Optional.of(message));
        when(deliveryPort.deliver(message)).thenReturn(EmailDeliveryPort.DeliveryResult.failed("TEMPORARY_PROVIDER"));

        service.processOne(1L, now);

        assertEquals(EmailOutboxStatus.FAILED, message.getStatus());
        assertEquals(1, message.getAttemptCount());
        assertEquals(now.plusSeconds(30), message.getNextAttemptAt());
        verify(attemptRepository).save(any(EmailDeliveryAttempt.class));

        EmailOutboxMessage terminal = message("retry-terminal", "hash");
        terminal = new EmailOutboxMessage(null, "retry-terminal", "hash", "registration", "v1",
                "guest@example.com", "Subject", null, "body", null, null, null, 1, now);
        when(outboxRepository.findForUpdate(5L)).thenReturn(Optional.of(terminal));
        service.processOne(5L, now);
        assertEquals(EmailOutboxStatus.DEAD_LETTER, terminal.getStatus());
    }

    @Test
    void processOne_FailClosedAdapterBecomesTerminalWithoutRawException() {
        EmailOutboxMessage message = message("disabled-1", "hash");
        when(outboxRepository.findForUpdate(2L)).thenReturn(Optional.of(message));
        when(deliveryPort.deliver(message)).thenReturn(EmailDeliveryPort.DeliveryResult.failed("DELIVERY_DISABLED"));

        service.processOne(2L, now);

        assertEquals(EmailOutboxStatus.DEAD_LETTER, message.getStatus());
        assertEquals("DELIVERY_DISABLED", message.getLastErrorCode());
        verify(auditService).append(any(OperationalAuditService.AuditCommand.class));
    }

    @Test
    void processOne_SuccessRecordsProviderIdentityAndAudit() {
        EmailOutboxMessage message = message("sent-1", "hash");
        when(outboxRepository.findForUpdate(3L)).thenReturn(Optional.of(message));
        when(deliveryPort.deliver(message)).thenReturn(EmailDeliveryPort.DeliveryResult.sent("provider-1"));

        service.processOne(3L, now);

        assertEquals(EmailOutboxStatus.SENT, message.getStatus());
        assertEquals(1, message.getAttemptCount());
        assertEquals("provider-1", message.getProviderMessageId());
        verify(attemptRepository).save(any(EmailDeliveryAttempt.class));
        verify(auditService).append(any(OperationalAuditService.AuditCommand.class));
    }

    @Test
    void manualRetry_ResetsAttemptWindowAndBouncedIsVisible() {
        EmailOutboxMessage message = message("manual-1", "hash");
        message.markFailed("SMTP_DELIVERY_FAILED", now, true, now);
        when(outboxRepository.findById(4L)).thenReturn(Optional.of(message));
        var retried = service.manualRetry(4L);
        assertEquals(EmailOutboxStatus.PENDING.name(), retried.status());
        assertEquals(1, retried.manualRetryCount());
        assertEquals(0, retried.attemptCount());

        var bounced = service.markBounced(4L, "550 mailbox unavailable");
        assertEquals(EmailOutboxStatus.BOUNCED.name(), bounced.status());
        assertEquals("550_mailbox_unavailable", bounced.lastErrorCode());
    }

    private EmailOutboxMessage message(String key, String ignoredHash) {
        EmailOutboxMessage message = new EmailOutboxMessage(null, key, ignoredHash, "registration", "v1",
                "guest@example.com", "Subject", null, "body", null, null, null, 2, now);
        try {
            var id = EmailOutboxMessage.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(message, 1L);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return message;
    }

    private EnqueueRequest request(String key, int maxAttempts) {
        return new EnqueueRequest(null, key, "registration", "v1", "guest@example.com",
                "Subject", null, "body", null, null, null, maxAttempts);
    }

    private String firstHash(EnqueueRequest request) {
        try {
            var method = EmailOutboxService.class.getDeclaredMethod("sha256", String.class);
            method.setAccessible(true);
            String input = String.join("\u001f", "", request.idempotencyKey(), request.templateKey(), request.templateVersion(),
                    request.recipientEmail(), request.subject(), "", request.bodyText(), "", "", "");
            return (String) method.invoke(service, input);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

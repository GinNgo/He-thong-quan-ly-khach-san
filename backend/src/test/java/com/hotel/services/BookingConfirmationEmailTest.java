package com.hotel.services;

import com.hotel.emailoutbox.EmailDeliveryAttempt;
import com.hotel.emailoutbox.EmailDeliveryAttemptRepository;
import com.hotel.emailoutbox.EmailDeliveryPort;
import com.hotel.emailoutbox.EmailOutboxDtos;
import com.hotel.emailoutbox.EmailOutboxMessage;
import com.hotel.emailoutbox.EmailOutboxRepository;
import com.hotel.emailoutbox.EmailOutboxService;
import com.hotel.emailoutbox.EmailOutboxStatus;
import com.hotel.observability.OperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingConfirmationEmailTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private EmailOutboxService emailOutboxService;

    private SimpleMeterRegistry registry;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        emailService = new EmailService(mailSender, new OperationalMetrics(registry), emailOutboxService);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@luxestay.test");
        ReflectionTestUtils.setField(emailService, "outboxEnabled", true);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void queuesVietnameseVersionedTemplateOnlyAfterCommit() throws NoSuchMethodException {
        Transactional boundary = EmailOutboxService.class
                .getMethod("enqueueAfterCommit", EmailOutboxDtos.EnqueueRequest.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, boundary.propagation());
        beginTransactionSynchronization();

        emailService.sendBookingConfirmation(
                "guest@example.com", "Nguyễn <An>", 42L, "2026-08-10", "2026-08-12", "vi-VN");

        verifyNoInteractions(emailOutboxService);
        completeCommit();

        ArgumentCaptor<EmailOutboxDtos.EnqueueRequest> captor =
                ArgumentCaptor.forClass(EmailOutboxDtos.EnqueueRequest.class);
        verify(emailOutboxService).enqueueAfterCommit(captor.capture());
        EmailOutboxDtos.EnqueueRequest request = captor.getValue();
        assertEquals("booking_confirmation", request.templateKey());
        assertEquals("v2.vi", request.templateVersion());
        assertTrue(request.idempotencyKey().startsWith("booking-confirmation:v2:vi:42:"));
        assertEquals("Xác nhận đặt phòng thành công - Mã đặt phòng: #42", request.subject());
        assertTrue(request.bodyHtml().contains("<html lang=\"vi\">"));
        assertTrue(request.bodyHtml().contains("Nguyễn &lt;An&gt;"));
        assertFalse(request.bodyHtml().contains("Nguyễn <An>"));
        assertTrue(request.bodyText().contains("Ngày nhận phòng: 2026-08-10"));
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void rolledBackBookingTransactionDoesNotQueueConfirmation() {
        beginTransactionSynchronization();

        emailService.sendBookingConfirmation(
                "guest@example.com", "Guest", 43L, "2026-08-10", "2026-08-12", "vi");

        completeRollback();
        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void rendersEnglishTemplateWithLocaleSpecificVersion() {
        emailService.sendBookingConfirmation(
                "guest@example.com", "Alex", 84L, "2026-09-01", "2026-09-03", "en-US");

        ArgumentCaptor<EmailOutboxDtos.EnqueueRequest> captor =
                ArgumentCaptor.forClass(EmailOutboxDtos.EnqueueRequest.class);
        verify(emailOutboxService).enqueueAfterCommit(captor.capture());
        EmailOutboxDtos.EnqueueRequest request = captor.getValue();
        assertEquals("v2.en", request.templateVersion());
        assertTrue(request.idempotencyKey().startsWith("booking-confirmation:v2:en:84:"));
        assertEquals("Booking confirmed - Reservation #84", request.subject());
        assertTrue(request.bodyHtml().contains("<html lang=\"en\">"));
        assertTrue(request.bodyHtml().contains("Your stay is confirmed"));
        assertTrue(request.bodyText().contains("Check-out: 2026-09-03"));
    }

    @Test
    void directVietnameseHtmlDeliveryUsesMultipartMimeMessage() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        ReflectionTestUtils.setField(emailService, "outboxEnabled", false);

        emailService.sendBookingConfirmation(
                "guest@example.com", "Khách kiểm thử", 2026L, "2026-08-20", "2026-08-22", "vi");

        verify(mailSender).send(message);
        assertEquals("Xác nhận đặt phòng thành công - Mã đặt phòng: #2026", message.getSubject());
        message.saveChanges();
        assertTrue(message.isMimeType("multipart/*"));
    }

    @Test
    void afterCommitQueueFailureIsContainedAndCannotReverseBookingState() {
        AtomicReference<String> bookingState = new AtomicReference<>("CONFIRMED");
        doThrow(new IllegalStateException("sandbox unavailable"))
                .when(emailOutboxService).enqueueAfterCommit(any(EmailOutboxDtos.EnqueueRequest.class));
        beginTransactionSynchronization();

        emailService.sendBookingConfirmation(
                "guest@example.com", "Guest", 91L, "2026-10-01", "2026-10-02", "vi");

        assertDoesNotThrow(this::completeCommit);
        assertEquals("CONFIRMED", bookingState.get());
        assertEquals(1, registry.get("hotel.external.operations")
                .tags("channel", "mail", "operation", "booking_confirmation", "outcome", "failure")
                .timer().count());
    }

    @Test
    void queuedBookingDeliveryFailureRetriesWithoutChangingCommittedBookingState() {
        emailService.sendBookingConfirmation(
                "guest@example.com", "Guest", 108L, "2026-11-05", "2026-11-07", "en");
        ArgumentCaptor<EmailOutboxDtos.EnqueueRequest> requestCaptor =
                ArgumentCaptor.forClass(EmailOutboxDtos.EnqueueRequest.class);
        verify(emailOutboxService).enqueueAfterCommit(requestCaptor.capture());
        EmailOutboxDtos.EnqueueRequest request = requestCaptor.getValue();

        EmailOutboxRepository outboxRepository = mock(EmailOutboxRepository.class);
        EmailDeliveryAttemptRepository attemptRepository = mock(EmailDeliveryAttemptRepository.class);
        EmailDeliveryPort deliveryPort = mock(EmailDeliveryPort.class);
        OperationalAuditService auditService = mock(OperationalAuditService.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 10, 0);
        EmailOutboxService deliveryService = new EmailOutboxService(
                outboxRepository,
                attemptRepository,
                deliveryPort,
                auditService,
                null,
                Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC),
                30,
                3600,
                5);
        EmailOutboxMessage message = new EmailOutboxMessage(
                request.hotelId(),
                request.idempotencyKey(),
                "request-hash",
                request.templateKey(),
                request.templateVersion(),
                request.recipientEmail(),
                request.subject(),
                request.bodyHtml(),
                request.bodyText(),
                null,
                null,
                null,
                5,
                now);
        ReflectionTestUtils.setField(message, "id", 108L);
        when(outboxRepository.findForUpdate(108L)).thenReturn(Optional.of(message));
        when(deliveryPort.deliver(message)).thenReturn(
                EmailDeliveryPort.DeliveryResult.failed("SANDBOX_TEMPORARY"),
                EmailDeliveryPort.DeliveryResult.sent("sandbox-message-108"));
        AtomicReference<String> bookingState = new AtomicReference<>("CONFIRMED");

        deliveryService.processOne(108L, now);
        assertEquals(EmailOutboxStatus.FAILED, message.getStatus());
        assertEquals(now.plusSeconds(30), message.getNextAttemptAt());
        assertEquals("CONFIRMED", bookingState.get());

        deliveryService.processOne(108L, now.plusSeconds(30));
        assertEquals(EmailOutboxStatus.SENT, message.getStatus());
        assertEquals(2, message.getAttemptCount());
        assertEquals("sandbox-message-108", message.getProviderMessageId());
        assertEquals("CONFIRMED", bookingState.get());
        verify(attemptRepository, org.mockito.Mockito.times(2)).save(any(EmailDeliveryAttempt.class));
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void completeCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    private void completeRollback() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        synchronizations.forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}

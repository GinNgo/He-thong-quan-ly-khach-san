package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.notifications.delivery.NotificationDeliveryOutbox;
import com.hotel.notifications.delivery.NotificationDeliveryOutboxRepository;
import com.hotel.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceReliabilityTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeliveryOutboxRepository outboxRepository;

    @Mock
    private NotificationIdempotencyWriter idempotencyWriter;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                notificationRepository, outboxRepository, idempotencyWriter);
    }

    @Test
    void systemNotificationAndDeliveryArePersistedTogether() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(41L);
            return notification;
        });
        when(outboxRepository.findByNotificationId(41L)).thenReturn(Optional.empty());

        Notification result = service.sendSystemNotification("SYSTEM", "Title", "Message");

        assertEquals(41L, result.getId());
        ArgumentCaptor<NotificationDeliveryOutbox> outbox =
                ArgumentCaptor.forClass(NotificationDeliveryOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("/topic/admin/notifications", outbox.getValue().getDestination());
        assertEquals(null, outbox.getValue().getRecipientUsername());
    }

    @Test
    void idempotentProducerDelegatesToTheTransactionalWriter() {
        Notification notification = notification(42L, "refund:approved:77");
        when(idempotencyWriter.createOrLoad(
                "refund:approved:77", "customer", 7L,
                "REFUND", "Refund approved", "Your refund was approved."))
                .thenReturn(notification);

        Notification result = service.sendUserNotificationOnce(
                " refund:approved:77 ", " customer ", 7L,
                "REFUND", "Refund approved", "Your refund was approved.");

        assertEquals(42L, result.getId());
        verify(idempotencyWriter).createOrLoad(
                "refund:approved:77", "customer", 7L,
                "REFUND", "Refund approved", "Your refund was approved.");
    }

    @Test
    void replayReturnsTheWriterOwnedNotificationAndDelivery() {
        Notification notification = notification(43L, "refund:approved:78");
        when(idempotencyWriter.createOrLoad(
                "refund:approved:78", "customer", 7L,
                "REFUND", "Refund approved", "Your refund was approved."))
                .thenReturn(notification);

        Notification result = service.sendUserNotificationOnce(
                "refund:approved:78", "customer", 7L,
                "REFUND", "Refund approved", "Your refund was approved.");

        assertEquals(43L, result.getId());
        verify(idempotencyWriter).createOrLoad(
                "refund:approved:78", "customer", 7L,
                "REFUND", "Refund approved", "Your refund was approved.");
    }

    @Test
    void idempotentProducerRejectsMissingOrOversizedKeys() {
        assertThrows(IllegalArgumentException.class, () -> service.sendUserNotificationOnce(
                " ", "customer", 7L, "SYSTEM", "Title", "Message"));
        assertThrows(IllegalArgumentException.class, () -> service.sendUserNotificationOnce(
                "x".repeat(161), "customer", 7L, "SYSTEM", "Title", "Message"));
        verify(idempotencyWriter, never()).createOrLoad(
                any(), any(), any(), any(), any(), any());
    }

    private Notification notification(Long id, String eventKey) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(7L);
        notification.setEventKey(eventKey);
        notification.setType("REFUND");
        notification.setTitle("Refund approved");
        notification.setMessage("Your refund was approved.");
        notification.setCreatedAt(java.time.LocalDateTime.now());
        return notification;
    }
}

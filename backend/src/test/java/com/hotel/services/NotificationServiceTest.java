package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.notifications.delivery.NotificationDeliveryOutbox;
import com.hotel.notifications.delivery.NotificationDeliveryOutboxRepository;
import com.hotel.notifications.preferences.NotificationPreferenceService;
import com.hotel.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDeliveryOutboxRepository deliveryOutboxRepository;
    @Mock private NotificationIdempotencyWriter idempotencyWriter;
    @Mock private NotificationPreferenceService preferenceService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                notificationRepository, deliveryOutboxRepository, idempotencyWriter, preferenceService);
    }

    @Test
    void userNotificationIsPersistedBeforeDurableTargetedDeliveryIsQueued() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 4, 6, 30);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(11L);
            return notification;
        });
        when(deliveryOutboxRepository.findByNotificationId(11L)).thenReturn(Optional.empty());

        Notification result = service.sendUserNotification(
                7L, "PROPERTY_APPROVAL", "Property approved", "Approved.", createdAt);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals(createdAt, saved.getValue().getCreatedAt());
        assertSame(saved.getValue(), result);
        ArgumentCaptor<NotificationDeliveryOutbox> queued = ArgumentCaptor.forClass(NotificationDeliveryOutbox.class);
        verify(deliveryOutboxRepository).save(queued.capture());
        assertEquals("7", queued.getValue().getRecipientUsername());
        assertEquals("/queue/notifications", queued.getValue().getDestination());
    }

    @Test
    void existingOutboxEntryPreventsDuplicateDeliveryQueueRows() {
        Notification saved = new Notification();
        saved.setId(11L);
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(deliveryOutboxRepository.findByNotificationId(11L))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(NotificationDeliveryOutbox.class)));

        Notification result = service.sendUserNotification(
                7L, "PROPERTY_APPROVAL", "Property approved", "Approved.", LocalDateTime.now());

        assertSame(saved, result);
        verify(deliveryOutboxRepository, never()).save(any());
    }

    @Test
    void repositoryFailurePropagatesSoWorkflowTransactionCanRollBack() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new IllegalStateException("notification store unavailable"));

        assertThrows(IllegalStateException.class, () -> service.sendUserNotification(
                7L, "PROPERTY_APPROVAL", "Property approved", "Approved.", LocalDateTime.now()));

        verify(deliveryOutboxRepository, never()).save(any());
    }
}

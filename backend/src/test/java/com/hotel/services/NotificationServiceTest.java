package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Test
    void userNotificationIsDurablyFlushedBeforeTargetedWebSocketPush() {
        NotificationService service = new NotificationService(notificationRepository, messagingTemplate);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 4, 6, 30);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = service.sendUserNotification(
                7L, "PROPERTY_APPROVAL", "Property approved", "Approved.", createdAt);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(saved.capture());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals(createdAt, saved.getValue().getCreatedAt());
        assertSame(saved.getValue(), result);
        verify(messagingTemplate).convertAndSendToUser(Long.toString(7L), "/queue/notifications", result);
    }

    @Test
    void websocketFailureDoesNotDiscardDurableNotification() {
        NotificationService service = new NotificationService(notificationRepository, messagingTemplate);
        Notification saved = new Notification();
        saved.setId(11L);
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(saved);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any());

        Notification result = service.sendUserNotification(
                7L, "PROPERTY_APPROVAL", "Property approved", "Approved.", LocalDateTime.now());

        assertSame(saved, result);
    }

    @Test
    void repositoryFailurePropagatesSoWorkflowTransactionCanRollBack() {
        NotificationService service = new NotificationService(notificationRepository, messagingTemplate);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new IllegalStateException("notification store unavailable"));

        assertThrows(IllegalStateException.class, () -> service.sendUserNotification(
                7L, "PROPERTY_APPROVAL", "Property approved", "Approved.", LocalDateTime.now()));

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }
}

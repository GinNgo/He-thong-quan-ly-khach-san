package com.hotel.services;

import com.hotel.entities.Notification;
<<<<<<< HEAD
import com.hotel.notifications.delivery.NotificationDeliveryOutbox;
import com.hotel.notifications.delivery.NotificationDeliveryOutboxRepository;
import com.hotel.notifications.preferences.NotificationPreferenceService;
=======
import com.hotel.exceptions.ResourceNotFoundException;
>>>>>>> codex/ui-functional-audit-polish
import com.hotel.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
<<<<<<< HEAD

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
=======
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
>>>>>>> codex/ui-functional-audit-polish
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

<<<<<<< HEAD
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
=======
    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, messagingTemplate, 30);
    }

    @Test
    void systemNotificationUsesProtectedAdminTopic() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification saved = notificationService.sendSystemNotification("SYSTEM", "Title", "Message");

        verify(messagingTemplate).convertAndSend("/topic/admin/notifications", saved);
    }

    @Test
    void personalNotificationUsesStandardUserQueue() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification saved = notificationService.sendUserNotification(
                "staff-user", 7L, "SYSTEM", "Title", "Message");

        verify(messagingTemplate).convertAndSendToUser(
                "staff-user", "/queue/notifications", saved);
    }

    @Test
    void idempotentPersonalNotificationDoesNotPersistOrPushAReplay() {
        Notification existing = new Notification();
        existing.setEventKey("refund:event:1");
        when(notificationRepository.findByEventKey("refund:event:1"))
                .thenReturn(java.util.Optional.empty(), java.util.Optional.of(existing));
        when(notificationRepository.save(any(Notification.class))).thenReturn(existing);

        notificationService.sendUserNotificationOnce(
                "refund:event:1", "staff-user", 7L, "PAYMENT", "Title", "Message");
        notificationService.sendUserNotificationOnce(
                "refund:event:1", "staff-user", 7L, "PAYMENT", "Title", "Message");

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                "staff-user", "/queue/notifications", existing);
        verify(messagingTemplate, never()).convertAndSend("/topic/admin/notifications", existing);
    }

    @Test
    void historyClampsPagingAndReportsRetainedUnreadCount() {
        Notification notification = notification(11L, false);
        when(notificationRepository.findVisibleToUser(eq(11L), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(notificationRepository.countUnreadVisibleToUser(eq(11L), any(LocalDateTime.class)))
                .thenReturn(4L);

        NotificationService.NotificationHistoryPage result =
                notificationService.getNotificationHistory(11L, -2, 500);

        assertEquals(List.of(notification), result.content());
        assertEquals(4L, result.unreadCount());
        assertEquals(30, result.retentionDays());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findVisibleToUser(
                eq(11L), any(LocalDateTime.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                pageable.getSort().getOrderFor("createdAt").getDirection());
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                pageable.getSort().getOrderFor("id").getDirection());
    }

    @Test
    void missingOrExpiredNotificationUsesStableNotFoundException() {
        when(notificationRepository.findByIdAndCreatedAtGreaterThanEqual(
                eq(99L), any(LocalDateTime.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead(99L, 11L));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void actorCannotMarkAnotherUsersNotification() {
        Notification foreign = notification(22L, false);
        when(notificationRepository.findByIdAndCreatedAtGreaterThanEqual(
                eq(3L), any(LocalDateTime.class))).thenReturn(Optional.of(foreign));

        assertThrows(AccessDeniedException.class,
                () -> notificationService.markAsRead(3L, 11L));
        assertFalse(foreign.isRead());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markingOwnedNotificationIsIdempotent() {
        Notification owned = notification(11L, false);
        when(notificationRepository.findByIdAndCreatedAtGreaterThanEqual(
                eq(3L), any(LocalDateTime.class))).thenReturn(Optional.of(owned));

        notificationService.markAsRead(3L, 11L);
        notificationService.markAsRead(3L, 11L);

        assertTrue(owned.isRead());
        verify(notificationRepository, times(1)).save(owned);
    }

    private Notification notification(Long userId, boolean read) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("SYSTEM");
        notification.setTitle("Title");
        notification.setMessage("Message");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(read);
        return notification;
>>>>>>> codex/ui-functional-audit-polish
    }
}

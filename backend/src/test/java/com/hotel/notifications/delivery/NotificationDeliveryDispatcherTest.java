package com.hotel.notifications.delivery;

import com.hotel.dtos.CustomerNotificationDTO;
import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import com.hotel.services.CustomerNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryDispatcherTest {

    @Mock
    private NotificationDeliveryOutboxRepository outboxRepository;

    @Mock
    private NotificationDeliveryAttemptRepository attemptRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CustomerNotificationService customerNotificationService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private NotificationDeliveryDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDeliveryDispatcher(
                outboxRepository,
                attemptRepository,
                notificationRepository,
                customerNotificationService,
                messagingTemplate,
                50,
                3,
                5);
    }

    @Test
    void successfulUserDeliveryIsAuditedAndNotRetried() {
        NotificationDeliveryOutbox delivery = delivery(11L, "customer", "/queue/notifications");
        Notification notification = notification(71L);
        CustomerNotificationDTO payload = payload(71L);
        when(outboxRepository.findDueForUpdate(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(delivery));
        when(notificationRepository.findById(71L)).thenReturn(Optional.of(notification));
        when(customerNotificationService.toDto(notification)).thenReturn(payload);

        assertEquals(1, dispatcher.dispatchDueDeliveries());

        verify(messagingTemplate).convertAndSendToUser(
                "customer", "/queue/notifications", payload);
        assertEquals(NotificationDeliveryStatus.DELIVERED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertTrue(delivery.getDeliveredAt() != null);
        ArgumentCaptor<NotificationDeliveryAttempt> attempt =
                ArgumentCaptor.forClass(NotificationDeliveryAttempt.class);
        verify(attemptRepository).save(attempt.capture());
        assertEquals("DELIVERED", attempt.getValue().getOutcome());
    }

    @Test
    void transientFailureRecordsAttemptAndSchedulesBoundedBackoff() {
        NotificationDeliveryOutbox delivery = delivery(12L, "customer", "/queue/notifications");
        Notification notification = notification(72L);
        CustomerNotificationDTO payload = payload(72L);
        LocalDateTime originalDueAt = delivery.getNextAttemptAt();
        when(outboxRepository.findDueForUpdate(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(delivery));
        when(notificationRepository.findById(72L)).thenReturn(Optional.of(notification));
        when(customerNotificationService.toDto(notification)).thenReturn(payload);
        doThrow(new IllegalStateException("broker offline"))
                .when(messagingTemplate)
                .convertAndSendToUser("customer", "/queue/notifications", payload);

        dispatcher.dispatchDueDeliveries();

        assertEquals(NotificationDeliveryStatus.RETRY, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertEquals("IllegalStateException", delivery.getLastErrorType());
        assertTrue(delivery.getNextAttemptAt().isAfter(originalDueAt));
        ArgumentCaptor<NotificationDeliveryAttempt> attempt =
                ArgumentCaptor.forClass(NotificationDeliveryAttempt.class);
        verify(attemptRepository).save(attempt.capture());
        assertEquals("FAILED", attempt.getValue().getOutcome());
        assertEquals("IllegalStateException", attempt.getValue().getErrorType());
    }

    @Test
    void missingNotificationMovesDeliveryToDeadLetterWithoutPayloadLeak() {
        NotificationDeliveryOutbox delivery = delivery(13L, null, "/topic/admin/notifications");
        when(outboxRepository.findDueForUpdate(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(delivery));
        when(notificationRepository.findById(73L)).thenReturn(Optional.empty());

        dispatcher.dispatchDueDeliveries();

        assertEquals(NotificationDeliveryStatus.DEAD, delivery.getStatus());
        assertEquals("MISSING_NOTIFICATION", delivery.getLastErrorType());
        ArgumentCaptor<NotificationDeliveryAttempt> attempt =
                ArgumentCaptor.forClass(NotificationDeliveryAttempt.class);
        verify(attemptRepository).save(attempt.capture());
        assertEquals("DEAD", attempt.getValue().getOutcome());
    }

    private NotificationDeliveryOutbox delivery(Long outboxId, String recipient, String destination) {
        NotificationDeliveryOutbox delivery = NotificationDeliveryOutbox.pending(
                outboxId + 60, recipient, destination, LocalDateTime.now().minusSeconds(1));
        delivery.setId(outboxId);
        return delivery;
    }

    private Notification notification(Long id) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(7L);
        notification.setType("BOOKING");
        notification.setTitle("Booking confirmed");
        notification.setMessage("Your booking is ready.");
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }

    private CustomerNotificationDTO payload(Long id) {
        return new CustomerNotificationDTO(
                id,
                "BOOKING",
                "Booking confirmed",
                "Your booking is ready.",
                false,
                LocalDateTime.now(),
                null,
                "/booking-history");
    }
}

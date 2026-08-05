package com.hotel.notifications.delivery;

import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import com.hotel.services.CustomerNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationDeliveryDispatcher {

    private static final List<NotificationDeliveryStatus> DUE_STATUSES = List.of(
            NotificationDeliveryStatus.PENDING,
            NotificationDeliveryStatus.RETRY);

    private final NotificationDeliveryOutboxRepository outboxRepository;
    private final NotificationDeliveryAttemptRepository attemptRepository;
    private final NotificationRepository notificationRepository;
    private final CustomerNotificationService customerNotificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseDelaySeconds;

    public NotificationDeliveryDispatcher(
            NotificationDeliveryOutboxRepository outboxRepository,
            NotificationDeliveryAttemptRepository attemptRepository,
            NotificationRepository notificationRepository,
            CustomerNotificationService customerNotificationService,
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.notifications.delivery-batch-size:50}") int batchSize,
            @Value("${app.notifications.delivery-max-attempts:5}") int maxAttempts,
            @Value("${app.notifications.delivery-base-delay-seconds:5}") long baseDelaySeconds) {
        this.outboxRepository = outboxRepository;
        this.attemptRepository = attemptRepository;
        this.notificationRepository = notificationRepository;
        this.customerNotificationService = customerNotificationService;
        this.messagingTemplate = messagingTemplate;
        this.batchSize = Math.min(Math.max(batchSize, 1), 200);
        this.maxAttempts = Math.min(Math.max(maxAttempts, 1), 20);
        this.baseDelaySeconds = Math.min(Math.max(baseDelaySeconds, 1), 3600);
    }

    @Scheduled(fixedDelayString = "${app.notifications.delivery-scan-ms:5000}")
    @Transactional
    public int dispatchDueDeliveries() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationDeliveryOutbox> due = outboxRepository.findDueForUpdate(
                DUE_STATUSES, now, PageRequest.of(0, batchSize));
        due.forEach(delivery -> dispatchOne(delivery, now));
        return due.size();
    }

    private void dispatchOne(NotificationDeliveryOutbox delivery, LocalDateTime now) {
        int attemptNumber = delivery.getAttemptCount() + 1;
        Notification notification = notificationRepository.findById(delivery.getNotificationId()).orElse(null);
        if (notification == null) {
            fail(delivery, attemptNumber, "MISSING_NOTIFICATION", now, true);
            return;
        }

        try {
            Object payload = customerNotificationService.toDto(notification);
            if (delivery.getRecipientUsername() == null) {
                messagingTemplate.convertAndSend(delivery.getDestination(), payload);
            } else {
                messagingTemplate.convertAndSendToUser(
                        delivery.getRecipientUsername(), delivery.getDestination(), payload);
            }
            delivery.setAttemptCount(attemptNumber);
            delivery.setStatus(NotificationDeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(now);
            delivery.setLastErrorType(null);
            attemptRepository.save(NotificationDeliveryAttempt.of(
                    delivery.getId(), attemptNumber, "DELIVERED", null, now));
        } catch (RuntimeException failure) {
            fail(delivery, attemptNumber, failure.getClass().getSimpleName(), now, false);
        }
    }

    private void fail(
            NotificationDeliveryOutbox delivery,
            int attemptNumber,
            String errorType,
            LocalDateTime now,
            boolean permanent) {
        boolean exhausted = permanent || attemptNumber >= maxAttempts;
        delivery.setAttemptCount(attemptNumber);
        delivery.setStatus(exhausted
                ? NotificationDeliveryStatus.DEAD
                : NotificationDeliveryStatus.RETRY);
        delivery.setLastErrorType(errorType);
        if (!exhausted) {
            delivery.setNextAttemptAt(now.plusSeconds(backoffSeconds(attemptNumber)));
        }
        attemptRepository.save(NotificationDeliveryAttempt.of(
                delivery.getId(), attemptNumber, exhausted ? "DEAD" : "FAILED", errorType, now));
    }

    private long backoffSeconds(int attemptNumber) {
        int exponent = Math.min(Math.max(attemptNumber - 1, 0), 10);
        return Math.min(baseDelaySeconds * (1L << exponent), 3600L);
    }
}

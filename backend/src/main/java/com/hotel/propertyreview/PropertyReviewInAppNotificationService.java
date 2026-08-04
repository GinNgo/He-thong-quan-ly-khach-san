package com.hotel.propertyreview;

import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
public class PropertyReviewInAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PropertyReviewInAppNotificationService.class);

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PropertyReviewInAppNotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Notification send(
            Long userId,
            String type,
            String title,
            String message,
            LocalDateTime createdAt) {
        if (userId == null) {
            throw new IllegalArgumentException("Notification recipient is required.");
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(createdAt == null ? LocalDateTime.now() : createdAt);
        notification.setRead(false);
        Notification saved = notificationRepository.saveAndFlush(notification);

        Runnable push = () -> push(saved);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
        return saved;
    }

    private void push(Notification notification) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(notification.getUserId()), "/queue/notifications", notification);
        } catch (RuntimeException websocketFailure) {
            log.warn("Durable property review notification {} could not be pushed over WebSocket",
                    notification.getId(), websocketFailure);
        }
    }
}

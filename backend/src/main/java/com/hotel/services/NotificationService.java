package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.notifications.delivery.NotificationDeliveryOutbox;
import com.hotel.notifications.delivery.NotificationDeliveryOutboxRepository;
import com.hotel.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryOutboxRepository deliveryOutboxRepository;
    private final NotificationIdempotencyWriter idempotencyWriter;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationDeliveryOutboxRepository deliveryOutboxRepository,
            NotificationIdempotencyWriter idempotencyWriter) {
        this.notificationRepository = notificationRepository;
        this.deliveryOutboxRepository = deliveryOutboxRepository;
        this.idempotencyWriter = idempotencyWriter;
    }

    @Transactional
    public Notification sendSystemNotification(String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(null); // System-wide for admins
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        Notification saved = notificationRepository.save(notification);
        enqueueDelivery(saved, null, "/topic/admin/notifications");
        return saved;
    }

    @Transactional
    public Notification sendUserNotification(
            String username,
            Long userId,
            String type,
            String title,
            String message) {
        Notification notification = newNotification(userId, type, title, message);
        Notification saved = notificationRepository.save(notification);
        enqueueDelivery(saved, requireRecipient(username), "/queue/notifications");
        return saved;
    }

    @Transactional
    public Notification sendUserNotificationOnce(
            String eventKey,
            String username,
            Long userId,
            String type,
            String title,
            String message) {
        String normalizedKey = requireEventKey(eventKey);
        String recipient = requireRecipient(username);
        return idempotencyWriter.createOrLoad(
                normalizedKey, recipient, userId, type, title, message);
    }

    public List<Notification> getAdminNotifications() {
        return notificationRepository.findByUserIdIsNullOrderByCreatedAtDesc();
    }

    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    private Notification newNotification(Long userId, String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        return notification;
    }

    private void enqueueDelivery(Notification notification, String recipientUsername, String destination) {
        if (deliveryOutboxRepository.findByNotificationId(notification.getId()).isPresent()) {
            return;
        }
        deliveryOutboxRepository.save(NotificationDeliveryOutbox.pending(
                notification.getId(), recipientUsername, destination, LocalDateTime.now()));
    }

    private String requireEventKey(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("Notification event key is required.");
        }
        String normalized = eventKey.trim();
        if (normalized.length() > 160) {
            throw new IllegalArgumentException("Notification event key exceeds 160 characters.");
        }
        return normalized;
    }

    private String requireRecipient(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Notification recipient username is required.");
        }
        return username.trim();
    }
}

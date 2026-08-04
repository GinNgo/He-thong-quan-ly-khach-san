package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Notification sendSystemNotification(String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(null); // System-wide for admins
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        Notification saved = notificationRepository.save(notification);
        
        // Push qua WebSocket STOMP
        messagingTemplate.convertAndSend("/topic/notifications", saved);
        return saved;
    }

    public Notification sendUserNotification(
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
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", saved);
        } catch (RuntimeException websocketFailure) {
            log.warn("Durable user notification {} could not be pushed over WebSocket", saved.getId(), websocketFailure);
        }
        return saved;
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
}

package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

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

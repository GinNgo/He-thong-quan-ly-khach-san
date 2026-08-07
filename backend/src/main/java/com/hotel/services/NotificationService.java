package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final int retentionDays;

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.notifications.retention-days:90}") int retentionDays) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.retentionDays = Math.min(Math.max(retentionDays, 1), 3650);
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
        messagingTemplate.convertAndSend("/topic/admin/notifications", saved);
        return saved;
    }

    public Notification sendUserNotification(
            String username,
            Long userId,
            String type,
            String title,
            String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", saved);
        return saved;
    }

    public Notification sendUserNotificationOnce(
            String eventKey,
            String username,
            Long userId,
            String type,
            String title,
            String message) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("Notification event key is required.");
        }
        return notificationRepository.findByEventKey(eventKey.trim())
                .orElseGet(() -> {
                    Notification notification = new Notification();
                    notification.setEventKey(eventKey.trim());
                    notification.setUserId(userId);
                    notification.setType(type);
                    notification.setTitle(title);
                    notification.setMessage(message);
                    notification.setCreatedAt(LocalDateTime.now());
                    notification.setRead(false);
                    Notification saved = notificationRepository.save(notification);
                    messagingTemplate.convertAndSendToUser(username, "/queue/notifications", saved);
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public NotificationHistoryPage getNotificationHistory(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LocalDateTime cutoff = retentionCutoff();
        Page<Notification> history = notificationRepository.findVisibleToUser(
                userId,
                cutoff,
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        long unreadCount = notificationRepository.countUnreadVisibleToUser(userId, cutoff);

        return new NotificationHistoryPage(
                history.getContent(),
                history.getTotalElements(),
                history.getTotalPages(),
                history.getNumber(),
                history.getSize(),
                history.isFirst(),
                history.isLast(),
                unreadCount,
                retentionDays);
    }

    @Transactional
    public void markAsRead(Long id, Long currentUserId) {
        Notification notification = notificationRepository.findByIdAndCreatedAtGreaterThanEqual(id, retentionCutoff())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getUserId() != null && !notification.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("Notification does not belong to the current user");
        }
        if (notification.isRead()) {
            return;
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private LocalDateTime retentionCutoff() {
        return LocalDateTime.now().minusDays(retentionDays);
    }

    public record NotificationHistoryPage(
            List<Notification> content,
            long totalElements,
            int totalPages,
            int number,
            int size,
            boolean first,
            boolean last,
            long unreadCount,
            int retentionDays) {
    }
}

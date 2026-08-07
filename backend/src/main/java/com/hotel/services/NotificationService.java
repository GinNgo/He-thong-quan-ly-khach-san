package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.exceptions.ResourceNotFoundException;
<<<<<<< HEAD
import com.hotel.notifications.delivery.NotificationDeliveryOutbox;
import com.hotel.notifications.delivery.NotificationDeliveryOutboxRepository;
import com.hotel.notifications.preferences.NotificationChannel;
import com.hotel.notifications.preferences.NotificationPreferenceService;
import com.hotel.repositories.NotificationRepository;
=======
import com.hotel.repositories.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
>>>>>>> codex/ui-functional-audit-polish
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
<<<<<<< HEAD
    private final NotificationDeliveryOutboxRepository deliveryOutboxRepository;
    private final NotificationIdempotencyWriter idempotencyWriter;
    private final NotificationPreferenceService preferenceService;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationDeliveryOutboxRepository deliveryOutboxRepository,
            NotificationIdempotencyWriter idempotencyWriter,
            NotificationPreferenceService preferenceService) {
        this.notificationRepository = notificationRepository;
        this.deliveryOutboxRepository = deliveryOutboxRepository;
        this.idempotencyWriter = idempotencyWriter;
        this.preferenceService = preferenceService;
=======
    private final SimpMessagingTemplate messagingTemplate;
    private final int retentionDays;

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.notifications.retention-days:90}") int retentionDays) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.retentionDays = Math.min(Math.max(retentionDays, 1), 3650);
>>>>>>> codex/ui-functional-audit-polish
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
<<<<<<< HEAD
        enqueueDelivery(saved, null, "/topic/admin/notifications");
        return saved;
    }

    @Transactional
    public Notification sendUserNotification(
            Long userId,
            String type,
            String title,
            String message,
            LocalDateTime createdAt) {
        if (userId == null) {
            throw new IllegalArgumentException("Notification recipient is required.");
        }
        Notification notification = newNotification(userId, type, title, message);
        notification.setCreatedAt(createdAt == null ? LocalDateTime.now() : createdAt);
        Notification saved = notificationRepository.save(notification);
        enqueueDelivery(saved, String.valueOf(userId), "/queue/notifications");
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
=======
        
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

>>>>>>> codex/ui-functional-audit-polish
    public Notification sendUserNotificationOnce(
            String eventKey,
            String username,
            Long userId,
            String type,
            String title,
            String message) {
<<<<<<< HEAD
        String normalizedKey = requireEventKey(eventKey);
        String recipient = requireRecipient(username);
        return idempotencyWriter.createOrLoad(
                normalizedKey, recipient, userId, type, title, message);
    }

    @Transactional
    public Optional<Notification> sendUserNotificationOnceIfEnabled(
            String eventKey,
            String username,
            Long userId,
            String type,
            String title,
            String message) {
        if (!preferenceService.isEnabled(userId, type, NotificationChannel.IN_APP)) {
            return Optional.empty();
        }
        return Optional.of(sendUserNotificationOnce(
                eventKey, username, userId, type, title, message));
    }
    public List<Notification> getNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdOrUserIdIsNullOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getUserId() != null && !notification.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification not found");
=======
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
>>>>>>> codex/ui-functional-audit-polish
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

<<<<<<< HEAD
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
=======
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
>>>>>>> codex/ui-functional-audit-polish
    }
}

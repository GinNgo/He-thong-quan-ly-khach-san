package com.hotel.services;

import com.hotel.dtos.CustomerNotificationDTO;
import com.hotel.entities.Notification;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;

@Service
public class CustomerNotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final int retentionDays;

    public CustomerNotificationService(
            NotificationRepository notificationRepository,
            @Value("${app.notifications.customer-retention-days:365}") int retentionDays) {
        this.notificationRepository = notificationRepository;
        this.retentionDays = Math.min(Math.max(retentionDays, 30), 3650);
    }

    @Transactional(readOnly = true)
    public CustomerNotificationPage getInbox(Long userId, int page, int size, boolean archived) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LocalDateTime cutoff = retentionCutoff();
        Page<Notification> notifications = notificationRepository.findCustomerHistory(
                userId, archived, cutoff,
                PageRequest.of(safePage, safeSize));

        List<CustomerNotificationDTO> content = notifications.getContent().stream()
                .map(this::toDto)
                .toList();
        return new CustomerNotificationPage(
                content,
                notifications.getTotalElements(),
                notifications.getTotalPages(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.isFirst(),
                notifications.isLast(),
                notificationRepository.countActiveUnread(userId, cutoff),
                archived,
                retentionDays);
    }

    @Transactional(readOnly = true)
    public UnreadCount getUnreadCount(Long userId) {
        return new UnreadCount(notificationRepository.countActiveUnread(userId, retentionCutoff()));
    }

    @Transactional
    public CustomerNotificationDTO markAsRead(Long notificationId, Long userId) {
        Notification notification = retainedOwned(notificationId, userId);
        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    @Transactional
    public CustomerNotificationDTO archive(Long notificationId, Long userId) {
        Notification notification = retainedOwned(notificationId, userId);
        if (notification.getArchivedAt() == null) {
            notification.setArchivedAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    @Transactional
    public CustomerNotificationDTO restore(Long notificationId, Long userId) {
        Notification notification = retainedOwned(notificationId, userId);
        if (notification.getArchivedAt() != null) {
            notification.setArchivedAt(null);
            notification = notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    private Notification retainedOwned(Long notificationId, Long userId) {
        return notificationRepository.findByIdAndUserIdAndCreatedAtGreaterThanEqual(
                        notificationId, userId, retentionCutoff())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
    }

    public CustomerNotificationDTO toDto(Notification notification) {
        return new CustomerNotificationDTO(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getArchivedAt(),
                deepLinkFor(notification.getType()));
    }

    private String deepLinkFor(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BOOKING", "RESERVATION", "PAYMENT" -> "/booking-history";
            case "INVOICE" -> "/my-invoices";
            case "REFUND" -> "/refunds";
            case "CHAT", "SUPPORT" -> "/?support=open";
            default -> "/notifications";
        };
    }

    private LocalDateTime retentionCutoff() {
        return LocalDateTime.now().minusDays(retentionDays);
    }

    public record CustomerNotificationPage(
            List<CustomerNotificationDTO> content,
            long totalElements,
            int totalPages,
            int number,
            int size,
            boolean first,
            boolean last,
            long unreadCount,
            boolean archived,
            int retentionDays) {
    }

    public record UnreadCount(long unreadCount) {
    }
}

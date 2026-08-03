package com.hotel.services;

import com.hotel.dtos.CustomerNotificationDTO;
import com.hotel.entities.Notification;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CustomerNotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    public CustomerNotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public CustomerNotificationPage getInbox(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
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
                notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    @Transactional(readOnly = true)
    public UnreadCount getUnreadCount(Long userId) {
        return new UnreadCount(notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    @Transactional
    public CustomerNotificationDTO markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    private CustomerNotificationDTO toDto(Notification notification) {
        return new CustomerNotificationDTO(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt(),
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

    public record CustomerNotificationPage(
            List<CustomerNotificationDTO> content,
            long totalElements,
            int totalPages,
            int number,
            int size,
            boolean first,
            boolean last,
            long unreadCount) {
    }

    public record UnreadCount(long unreadCount) {
    }
}

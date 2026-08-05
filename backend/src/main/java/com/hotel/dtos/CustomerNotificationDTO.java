package com.hotel.dtos;

import java.time.LocalDateTime;

public record CustomerNotificationDTO(
        Long id,
        String type,
        String title,
        String message,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime archivedAt,
        String deepLink) {
}

package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.notifications.delivery.NotificationDeliveryOutbox;
import com.hotel.notifications.delivery.NotificationDeliveryOutboxRepository;
import com.hotel.repositories.NotificationRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationIdempotencyWriter {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryOutboxRepository outboxRepository;
    private final JdbcTemplate jdbcTemplate;

    public NotificationIdempotencyWriter(
            NotificationRepository notificationRepository,
            NotificationDeliveryOutboxRepository outboxRepository,
            JdbcTemplate jdbcTemplate) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Notification createOrLoad(
            String eventKey,
            String recipient,
            Long userId,
            String type,
            String title,
            String message) {
        mergeNotification(eventKey, userId, type, title, message, LocalDateTime.now());
        Notification notification = requireExisting(eventKey);
        ensureOutbox(notification, recipient);
        return notification;
    }

    private Notification requireExisting(String eventKey) {
        return notificationRepository.findByEventKey(eventKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification idempotency row is not committed yet."));
    }

    private void ensureOutbox(Notification notification, String recipient) {
        if (outboxRepository.findByNotificationId(notification.getId()).isPresent()) {
            return;
        }
        outboxRepository.save(NotificationDeliveryOutbox.pending(
                notification.getId(), recipient, "/queue/notifications", LocalDateTime.now()));
    }

    private void mergeNotification(
            String eventKey,
            Long userId,
            String type,
            String title,
            String message,
            LocalDateTime createdAt) {
        String databaseProduct = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
        if (databaseProduct != null && databaseProduct.toLowerCase().contains("microsoft sql server")) {
            jdbcTemplate.update("""
                    MERGE dbo.notifications WITH (HOLDLOCK) AS target
                    USING (VALUES (?, ?, ?, ?, ?, ?, ?)) AS source
                        (event_key, user_id, type, title, message, is_read, created_at)
                    ON target.event_key = source.event_key
                    WHEN NOT MATCHED THEN
                        INSERT (event_key, user_id, type, title, message, is_read, created_at)
                        VALUES (source.event_key, source.user_id, source.type, source.title,
                                source.message, source.is_read, source.created_at);
                    """, eventKey, userId, type, title, message, false, createdAt);
            return;
        }

        // H2 uses KEY for the focused concurrency harness; production uses the lock-safe SQL Server MERGE above.
        jdbcTemplate.update("""
                MERGE INTO notifications
                    (event_key, user_id, type, title, message, is_read, created_at)
                KEY (event_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, eventKey, userId, type, title, message, false, createdAt);
    }
}

package com.hotel.notifications.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationDeliveryAttemptRepository
        extends JpaRepository<NotificationDeliveryAttempt, Long> {

    List<NotificationDeliveryAttempt> findByOutboxIdOrderByAttemptNumber(Long outboxId);
}

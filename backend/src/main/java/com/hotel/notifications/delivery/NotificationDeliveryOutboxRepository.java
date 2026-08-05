package com.hotel.notifications.delivery;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryOutboxRepository
        extends JpaRepository<NotificationDeliveryOutbox, Long> {

    Optional<NotificationDeliveryOutbox> findByNotificationId(Long notificationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select delivery
            from NotificationDeliveryOutbox delivery
            where delivery.status in :statuses
              and delivery.nextAttemptAt <= :now
            order by delivery.id
            """)
    List<NotificationDeliveryOutbox> findDueForUpdate(
            @Param("statuses") Collection<NotificationDeliveryStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}

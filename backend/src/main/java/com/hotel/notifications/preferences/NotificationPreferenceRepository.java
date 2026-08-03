package com.hotel.notifications.preferences;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserId(Long userId);

    Optional<NotificationPreference> findByUserIdAndEventClassAndChannel(
            Long userId,
            NotificationEventClass eventClass,
            NotificationChannel channel);
}

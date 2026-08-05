package com.hotel.integration;

import com.hotel.entities.Notification;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.notifications.preferences.NotificationChannel;
import com.hotel.notifications.preferences.NotificationEventClass;
import com.hotel.notifications.preferences.NotificationPreferenceRepository;
import com.hotel.notifications.preferences.NotificationPreferenceService;
import com.hotel.repositories.NotificationRepository;
import com.hotel.services.CustomerNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.notifications.customer-retention-days=365"
})
@Import({CustomerNotificationService.class, NotificationPreferenceService.class})
class NotificationLifecycleDataJpaIntegrationTest {

    @Autowired
    private CustomerNotificationService notificationService;

    @Autowired
    private NotificationPreferenceService preferenceService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Test
    void archiveHistoryIsRetainedAndForeignRowsRemainNonEnumerable() {
        Notification owned = save(7L, "BOOKING", LocalDateTime.now());
        Notification foreign = save(8L, "REFUND", LocalDateTime.now());
        save(7L, "INVOICE", LocalDateTime.now().minusDays(366));

        notificationService.archive(owned.getId(), 7L);

        var active = notificationService.getInbox(7L, 0, 20, false);
        var archived = notificationService.getInbox(7L, 0, 20, true);
        assertTrue(active.content().isEmpty());
        assertEquals(List.of(owned.getId()), archived.content().stream().map(row -> row.id()).toList());
        assertEquals(365, archived.retentionDays());
        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.archive(foreign.getId(), 7L));

        notificationService.restore(owned.getId(), 7L);
        assertEquals(List.of(owned.getId()), notificationService.getInbox(7L, 0, 20, false)
                .content().stream().map(row -> row.id()).toList());
    }

    @Test
    void preferenceRowsAreScopedToAuthenticatedUserAndMandatoryInAppRemainsEnabled() {
        preferenceService.update(7L, List.of(new NotificationPreferenceService.PreferenceUpdate(
                NotificationEventClass.MARKETING,
                NotificationChannel.EMAIL,
                true)));

        var saved = preferenceRepository.findByUserIdAndEventClassAndChannel(
                7L, NotificationEventClass.MARKETING, NotificationChannel.EMAIL).orElseThrow();
        assertTrue(saved.isEnabled());
        assertTrue(preferenceService.isEnabled(7L, "PAYMENT", NotificationChannel.IN_APP));
        assertThrows(IllegalArgumentException.class, () -> preferenceService.update(7L, List.of(
                new NotificationPreferenceService.PreferenceUpdate(
                        NotificationEventClass.PAYMENT,
                        NotificationChannel.IN_APP,
                        false))));
    }

    private Notification save(Long userId, String type, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(type + " title");
        notification.setMessage(type + " message");
        notification.setCreatedAt(createdAt);
        notification.setRead(false);
        return notificationRepository.saveAndFlush(notification);
    }
}

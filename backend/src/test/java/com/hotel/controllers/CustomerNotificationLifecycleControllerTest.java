package com.hotel.controllers;

import com.hotel.notifications.preferences.NotificationChannel;
import com.hotel.notifications.preferences.NotificationEventClass;
import com.hotel.notifications.preferences.NotificationPreferenceService;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.CustomerNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerNotificationLifecycleControllerTest {

    @Mock
    private CustomerNotificationService notificationService;

    @Mock
    private NotificationPreferenceService preferenceService;

    @Mock
    private CustomUserDetails principal;

    private CustomerNotificationController notificationController;
    private CustomerNotificationPreferenceController preferenceController;

    @BeforeEach
    void setUp() {
        notificationController = new CustomerNotificationController(notificationService);
        preferenceController = new CustomerNotificationPreferenceController(preferenceService);
        when(principal.getUserId()).thenReturn(7L);
    }

    @Test
    void archiveRestoreAndHistoryAlwaysUseAuthenticatedUserId() {
        notificationController.getInbox(principal, 2, 10, true);
        notificationController.archive(81L, principal);
        notificationController.restore(81L, principal);

        verify(notificationService).getInbox(7L, 2, 10, true);
        verify(notificationService).archive(81L, 7L);
        verify(notificationService).restore(81L, 7L);
    }

    @Test
    void preferenceUpdateIgnoresAnyExternalUserIdentity() {
        var updates = List.of(new NotificationPreferenceService.PreferenceUpdate(
                NotificationEventClass.MARKETING,
                NotificationChannel.EMAIL,
                true));

        preferenceController.updatePreferences(
                principal,
                new CustomerNotificationPreferenceController.PreferenceUpdateRequest(updates));

        verify(preferenceService).update(7L, updates);
    }
}

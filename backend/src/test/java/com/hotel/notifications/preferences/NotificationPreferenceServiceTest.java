package com.hotel.notifications.preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository repository;

    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(repository);
    }

    @Test
    void mandatoryInAppEventsAreEnabledAndLockedByDefault() {
        when(repository.findByUserId(7L)).thenReturn(List.of());

        var booking = service.getPreferences(7L).stream()
                .filter(group -> group.eventClass() == NotificationEventClass.BOOKING)
                .findFirst().orElseThrow();
        var inApp = booking.channels().stream()
                .filter(channel -> channel.channel() == NotificationChannel.IN_APP)
                .findFirst().orElseThrow();

        assertTrue(booking.mandatory());
        assertTrue(inApp.enabled());
        assertTrue(inApp.locked());
    }

    @Test
    void optionalMarketingChannelsAreConsentOffByDefault() {
        when(repository.findByUserIdAndEventClassAndChannel(
                7L, NotificationEventClass.MARKETING, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());
        when(repository.findByUserIdAndEventClassAndChannel(
                7L, NotificationEventClass.MARKETING, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());

        assertFalse(service.isEnabled(7L, "PROMOTION", NotificationChannel.IN_APP));
        assertFalse(service.isEnabled(7L, "MARKETING", NotificationChannel.EMAIL));
    }

    @Test
    void mandatoryInAppPreferenceCannotBeDisabled() {
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, List.of(
                new NotificationPreferenceService.PreferenceUpdate(
                        NotificationEventClass.PAYMENT,
                        NotificationChannel.IN_APP,
                        false))));
        verify(repository, never()).save(any());
    }

    @Test
    void updateAlwaysUsesTheAuthenticatedUserId() {
        when(repository.findByUserIdAndEventClassAndChannel(
                7L, NotificationEventClass.MARKETING, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(repository.findByUserId(7L)).thenReturn(List.of());

        service.update(7L, List.of(new NotificationPreferenceService.PreferenceUpdate(
                NotificationEventClass.MARKETING,
                NotificationChannel.EMAIL,
                true)));

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(preference ->
                preference.getUserId().equals(7L)
                        && preference.getEventClass() == NotificationEventClass.MARKETING
                        && preference.getChannel() == NotificationChannel.EMAIL
                        && preference.isEnabled()));
    }
}

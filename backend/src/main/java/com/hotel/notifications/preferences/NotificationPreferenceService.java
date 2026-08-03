package com.hotel.notifications.preferences;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PreferenceGroup> getPreferences(Long userId) {
        Map<NotificationEventClass, Map<NotificationChannel, Boolean>> stored = new EnumMap<>(
                NotificationEventClass.class);
        repository.findByUserId(userId).forEach(preference -> stored
                .computeIfAbsent(preference.getEventClass(), ignored -> new EnumMap<>(NotificationChannel.class))
                .put(preference.getChannel(), preference.isEnabled()));

        return Arrays.stream(NotificationEventClass.values())
                .map(eventClass -> new PreferenceGroup(
                        eventClass,
                        eventClass.getLabel(),
                        eventClass.isMandatory(),
                        Arrays.stream(NotificationChannel.values())
                                .map(channel -> new ChannelPreference(
                                        channel,
                                        effective(stored, eventClass, channel),
                                        eventClass.isMandatory() && channel == NotificationChannel.IN_APP))
                                .toList()))
                .toList();
    }

    @Transactional
    public List<PreferenceGroup> update(Long userId, List<PreferenceUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("At least one notification preference is required.");
        }
        LocalDateTime now = LocalDateTime.now();
        for (PreferenceUpdate update : updates) {
            if (update == null || update.eventClass() == null || update.channel() == null) {
                throw new IllegalArgumentException("Notification preference class and channel are required.");
            }
            if (update.eventClass().isMandatory()
                    && update.channel() == NotificationChannel.IN_APP
                    && !update.enabled()) {
                throw new IllegalArgumentException("Mandatory in-app notifications cannot be disabled.");
            }
            NotificationPreference preference = repository
                    .findByUserIdAndEventClassAndChannel(
                            userId, update.eventClass(), update.channel())
                    .orElseGet(NotificationPreference::new);
            preference.setUserId(userId);
            preference.setEventClass(update.eventClass());
            preference.setChannel(update.channel());
            preference.setEnabled(update.enabled());
            preference.setUpdatedAt(now);
            repository.save(preference);
        }
        return getPreferences(userId);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId, String notificationType, NotificationChannel channel) {
        NotificationEventClass eventClass = NotificationEventClass.fromNotificationType(notificationType);
        if (eventClass.isMandatory() && channel == NotificationChannel.IN_APP) {
            return true;
        }
        return repository.findByUserIdAndEventClassAndChannel(userId, eventClass, channel)
                .map(NotificationPreference::isEnabled)
                .orElseGet(() -> defaultEnabled(eventClass, channel));
    }

    private boolean effective(
            Map<NotificationEventClass, Map<NotificationChannel, Boolean>> stored,
            NotificationEventClass eventClass,
            NotificationChannel channel) {
        if (eventClass.isMandatory() && channel == NotificationChannel.IN_APP) {
            return true;
        }
        return stored.getOrDefault(eventClass, Map.of())
                .getOrDefault(channel, defaultEnabled(eventClass, channel));
    }

    private boolean defaultEnabled(NotificationEventClass eventClass, NotificationChannel channel) {
        return eventClass.isMandatory() && channel == NotificationChannel.EMAIL;
    }

    public record PreferenceGroup(
            NotificationEventClass eventClass,
            String label,
            boolean mandatory,
            List<ChannelPreference> channels) {
    }

    public record ChannelPreference(
            NotificationChannel channel,
            boolean enabled,
            boolean locked) {
    }

    public record PreferenceUpdate(
            NotificationEventClass eventClass,
            NotificationChannel channel,
            boolean enabled) {
    }
}

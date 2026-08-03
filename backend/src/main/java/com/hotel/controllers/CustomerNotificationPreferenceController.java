package com.hotel.controllers;

import com.hotel.notifications.preferences.NotificationPreferenceService;
import com.hotel.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/notifications/preferences")
public class CustomerNotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public CustomerNotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationPreferenceService.PreferenceGroup>> getPreferences(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(preferenceService.getPreferences(currentUser.getUserId()));
    }

    @PutMapping
    public ResponseEntity<List<NotificationPreferenceService.PreferenceGroup>> updatePreferences(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestBody PreferenceUpdateRequest request) {
        return ResponseEntity.ok(preferenceService.update(
                currentUser.getUserId(), request.preferences()));
    }

    public record PreferenceUpdateRequest(
            List<NotificationPreferenceService.PreferenceUpdate> preferences) {
    }
}

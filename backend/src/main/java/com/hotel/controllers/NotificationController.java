package com.hotel.controllers;

import com.hotel.entities.Notification;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*", maxAge = 3600)
@Permission(function = FunctionCode.REPORT, action = ActionCode.VIEW)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Lấy thông báo hệ thống (cho admin)
    @GetMapping
    public ResponseEntity<List<Notification>> getAdminNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(requireUserId(currentUser)));
    }

    // Đánh dấu đã đọc
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markAsRead(id, requireUserId(currentUser));
        return ResponseEntity.ok().build();
    }

    private Long requireUserId(CustomUserDetails currentUser) {
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new AccessDeniedException("Authoritative notification user context is required");
        }
        return currentUser.getUserId();
    }
}

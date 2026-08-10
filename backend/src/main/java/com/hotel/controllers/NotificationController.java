package com.hotel.controllers;

import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Lấy thông báo hệ thống (cho admin)
    @GetMapping
    @Permission(function = FunctionCode.REPORT, action = ActionCode.VIEW)
    public ResponseEntity<NotificationService.NotificationHistoryPage> getAdminNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getNotificationHistory(currentUser.getUserId(), page, size));
    }

    // Đánh dấu đã đọc
    @PostMapping("/{id}/read")
    @Permission(function = FunctionCode.REPORT, action = ActionCode.VIEW)
    public ResponseEntity<?> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markAsRead(id, currentUser.getUserId());
        return ResponseEntity.ok().build();
    }
}

package com.hotel.controllers;

<<<<<<< HEAD
import com.hotel.entities.Notification;
=======
>>>>>>> codex/ui-functional-audit-polish
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.NotificationService;
import org.springframework.http.ResponseEntity;
<<<<<<< HEAD
import org.springframework.security.access.AccessDeniedException;
=======
>>>>>>> codex/ui-functional-audit-polish
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
<<<<<<< HEAD
@CrossOrigin(origins = "*", maxAge = 3600)
@Permission(function = FunctionCode.REPORT, action = ActionCode.VIEW)
=======
>>>>>>> codex/ui-functional-audit-polish
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Lấy thông báo hệ thống (cho admin)
    @GetMapping
<<<<<<< HEAD
    public ResponseEntity<List<Notification>> getAdminNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(requireUserId(currentUser)));
=======
    @Permission(function = FunctionCode.REPORT, action = ActionCode.VIEW)
    public ResponseEntity<NotificationService.NotificationHistoryPage> getAdminNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getNotificationHistory(currentUser.getUserId(), page, size));
>>>>>>> codex/ui-functional-audit-polish
    }

    // Đánh dấu đã đọc
    @PostMapping("/{id}/read")
<<<<<<< HEAD
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markAsRead(id, requireUserId(currentUser));
=======
    @Permission(function = FunctionCode.REPORT, action = ActionCode.VIEW)
    public ResponseEntity<?> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markAsRead(id, currentUser.getUserId());
>>>>>>> codex/ui-functional-audit-polish
        return ResponseEntity.ok().build();
    }

    private Long requireUserId(CustomUserDetails currentUser) {
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new AccessDeniedException("Authoritative notification user context is required");
        }
        return currentUser.getUserId();
    }
}

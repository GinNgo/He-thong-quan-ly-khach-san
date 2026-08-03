package com.hotel.controllers;

import com.hotel.dtos.CustomerNotificationDTO;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.CustomerNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/notifications")
public class CustomerNotificationController {

    private final CustomerNotificationService customerNotificationService;

    public CustomerNotificationController(CustomerNotificationService customerNotificationService) {
        this.customerNotificationService = customerNotificationService;
    }

    @GetMapping
    public ResponseEntity<CustomerNotificationService.CustomerNotificationPage> getInbox(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(customerNotificationService.getInbox(currentUser.getUserId(), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<CustomerNotificationService.UnreadCount> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(customerNotificationService.getUnreadCount(currentUser.getUserId()));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<CustomerNotificationDTO> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(customerNotificationService.markAsRead(notificationId, currentUser.getUserId()));
    }
}

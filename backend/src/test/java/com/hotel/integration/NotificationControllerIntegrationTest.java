package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void unauthenticatedNotificationRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actorWithoutReportPermissionIsDenied() throws Exception {
        mockMvc.perform(get("/api/notifications").with(user(principal(7L, Map.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listContainsOnlySystemAndCurrentUserNotifications() throws Exception {
        saveNotification(null, "system");
        saveNotification(7L, "mine");
        saveNotification(8L, "foreign");

        mockMvc.perform(get("/api/notifications")
                        .with(user(principal(7L, Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.title == 'system')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'mine')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'foreign')]").doesNotExist());
    }

    @Test
    void currentUserCanMarkOwnedNotificationAsRead() throws Exception {
        Notification notification = saveNotification(7L, "mine");

        mockMvc.perform(post("/api/notifications/{id}/read", notification.getId())
                        .with(user(principal(7L, Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(
                notificationRepository.findById(notification.getId()).orElseThrow().isRead());

        mockMvc.perform(get("/api/notifications")
                        .with(user(principal(7L, Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isRead").value(true))
                .andExpect(jsonPath("$[0].read").doesNotExist());
    }

    @Test
    void userCannotMarkAnotherUsersNotificationAsRead() throws Exception {
        Notification notification = saveNotification(8L, "foreign");

        mockMvc.perform(post("/api/notifications/{id}/read", notification.getId())
                        .with(user(principal(7L, Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isForbidden());
    }

    private Notification saveNotification(Long userId, String title) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("SYSTEM");
        notification.setTitle(title);
        notification.setMessage(title + " message");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepository.saveAndFlush(notification);
    }

    private CustomUserDetails principal(Long userId, Map<FunctionCode, Integer> masks) {
        return new CustomUserDetails(
                "notification-user-" + userId,
                "hash",
                Set.of(new SimpleGrantedAuthority("STAFF")),
                masks,
                userId,
                null,
                Map.of());
    }
}

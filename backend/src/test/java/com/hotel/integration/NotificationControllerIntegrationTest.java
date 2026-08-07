package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = {
                "payment.property.encryption-key=test-property-payment-encryption-key",
                "app.notifications.retention-days=30"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void clearNotifications() {
        notificationRepository.deleteAll();
    }

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
    void paginatedHistoryContainsOnlyRetainedSystemAndCurrentStaffNotifications() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        saveNotification(null, "system-newest", now.minusMinutes(1));
        saveNotification(7L, "mine-second", now.minusMinutes(2));
        saveNotification(7L, "mine-third", now.minusMinutes(3));
        saveNotification(8L, "foreign", now);
        saveNotification(7L, "expired", now.minusDays(31));

        mockMvc.perform(get("/api/notifications?page=0&size=2")
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("system-newest"))
                .andExpect(jsonPath("$.content[1].title").value("mine-second"))
                .andExpect(jsonPath("$.content[?(@.title == 'foreign')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.title == 'expired')]").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.retentionDays").value(30));

        mockMvc.perform(get("/api/notifications?page=1&size=2")
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("mine-third"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void adminAndStaffReceiveIndependentOwnedRowsAlongsideSystemHistory() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        saveNotification(null, "system", now.minusMinutes(1));
        saveNotification(7L, "staff-owned", now.minusMinutes(2));
        saveNotification(8L, "admin-owned", now.minusMinutes(3));

        mockMvc.perform(get("/api/notifications")
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.title == 'staff-owned')]").exists())
                .andExpect(jsonPath("$.content[?(@.title == 'admin-owned')]").doesNotExist());

        mockMvc.perform(get("/api/notifications")
                        .with(user(principal(8L, "ADMIN",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.title == 'admin-owned')]").exists())
                .andExpect(jsonPath("$.content[?(@.title == 'staff-owned')]").doesNotExist());
    }

    @Test
    void currentUserCanMarkOwnedNotificationAsRead() throws Exception {
        Notification notification = saveNotification(7L, "mine", LocalDateTime.now());

        mockMvc.perform(post("/api/notifications/{id}/read", notification.getId())
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(
                notificationRepository.findById(notification.getId()).orElseThrow().isRead());

        mockMvc.perform(get("/api/notifications")
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].isRead").value(true))
                .andExpect(jsonPath("$.content[0].read").doesNotExist())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(post("/api/notifications/{id}/read", notification.getId())
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotMarkAnotherUsersNotificationAsRead() throws Exception {
        Notification notification = saveNotification(8L, "foreign", LocalDateTime.now());

        mockMvc.perform(post("/api/notifications/{id}/read", notification.getId())
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertFalse(notificationRepository.findById(notification.getId()).orElseThrow().isRead());
    }

    @Test
    void missingAndRetentionExpiredNotificationsReturnStableNotFoundEnvelope() throws Exception {
        Notification expired = saveNotification(7L, "expired", LocalDateTime.now().minusDays(31));

        mockMvc.perform(post("/api/notifications/{id}/read", expired.getId())
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.correlationId", not(blankOrNullString())));

        mockMvc.perform(post("/api/notifications/{id}/read", Long.MAX_VALUE)
                        .with(user(principal(7L, "RECEPTIONIST",
                                Map.of(FunctionCode.REPORT, ActionCode.VIEW)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private Notification saveNotification(Long userId, String title, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("SYSTEM");
        notification.setTitle(title);
        notification.setMessage(title + " message");
        notification.setRead(false);
        notification.setCreatedAt(createdAt);
        return notificationRepository.saveAndFlush(notification);
    }

    private CustomUserDetails principal(Long userId, Map<FunctionCode, Integer> masks) {
        return principal(userId, "STAFF", masks);
    }

    private CustomUserDetails principal(Long userId, String role, Map<FunctionCode, Integer> masks) {
        return new CustomUserDetails(
                "notification-user-" + userId,
                "hash",
                Set.of(new SimpleGrantedAuthority(role)),
                masks,
                userId,
                null,
                Map.of());
    }
}

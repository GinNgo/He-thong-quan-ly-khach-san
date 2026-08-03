package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Notification;
import com.hotel.repositories.NotificationRepository;
import com.hotel.security.CustomUserDetails;
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
class CustomerNotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void inboxRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/customer/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerSeesOnlyOwnNotificationsWithUnreadCountAndDeepLinks() throws Exception {
        saveNotification(7L, "BOOKING", "booking", false);
        saveNotification(7L, "INVOICE", "invoice", true);
        saveNotification(8L, "REFUND", "foreign", false);
        saveNotification(null, "SYSTEM", "admin broadcast", false);

        mockMvc.perform(get("/api/customer/notifications")
                        .with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.title == 'booking')].deepLink")
                        .value("/booking-history"))
                .andExpect(jsonPath("$.content[?(@.title == 'invoice')].deepLink")
                        .value("/my-invoices"))
                .andExpect(jsonPath("$.content[?(@.title == 'foreign')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.title == 'admin broadcast')]").doesNotExist())
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/customer/notifications/unread-count")
                        .with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void customerCanMarkOwnNotificationReadButCannotProbeAnotherUsersRow() throws Exception {
        Notification owned = saveNotification(7L, "REFUND", "mine", false);
        Notification foreign = saveNotification(8L, "REFUND", "foreign", false);

        mockMvc.perform(post("/api/customer/notifications/{id}/read", owned.getId())
                        .with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        org.junit.jupiter.api.Assertions.assertTrue(
                notificationRepository.findById(owned.getId()).orElseThrow().isRead());

        mockMvc.perform(post("/api/customer/notifications/{id}/read", foreign.getId())
                        .with(user(principal(7L))))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertFalse(
                notificationRepository.findById(foreign.getId()).orElseThrow().isRead());
    }

    private Notification saveNotification(Long userId, String type, String title, boolean read) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(title + " message");
        notification.setRead(read);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepository.saveAndFlush(notification);
    }

    private CustomUserDetails principal(Long userId) {
        return new CustomUserDetails(
                "customer-" + userId,
                "hash",
                Set.of(new SimpleGrantedAuthority("CUSTOMER")),
                Map.of(),
                userId,
                null,
                Map.of());
    }
}

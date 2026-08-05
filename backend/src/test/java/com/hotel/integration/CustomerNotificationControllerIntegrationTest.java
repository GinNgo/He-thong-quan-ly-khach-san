package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Notification;
import com.hotel.notifications.preferences.NotificationChannel;
import com.hotel.notifications.preferences.NotificationEventClass;
import com.hotel.notifications.preferences.NotificationPreferenceRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = {
                "payment.property.encryption-key=test-property-payment-encryption-key",
                "spring.datasource.url=jdbc:h2:mem:customer-notifications;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerNotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

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

        mockMvc.perform(post("/api/customer/notifications/{id}/archive", owned.getId())
                        .with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mockMvc.perform(get("/api/customer/notifications")
                        .param("archived", "true")
                        .with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(owned.getId()));

        mockMvc.perform(put("/api/customer/notifications/{id}/restore", owned.getId())
                        .with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").doesNotExist());

        mockMvc.perform(post("/api/customer/notifications/{id}/read", foreign.getId())
                        .with(user(principal(7L))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/customer/notifications/{id}/archive", foreign.getId())
                        .with(user(principal(7L))))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertFalse(
                notificationRepository.findById(foreign.getId()).orElseThrow().isRead());
    }

    @Test
    void preferenceEndpointsUseAuthenticatedUserAndProtectMandatoryInAppEvents() throws Exception {
        mockMvc.perform(get("/api/customer/notifications/preferences")
                        .with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventClass == 'BOOKING')].mandatory").value(true))
                .andExpect(jsonPath("$[?(@.eventClass == 'MARKETING')].mandatory").value(false));

        mockMvc.perform(put("/api/customer/notifications/preferences")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferences":[{"eventClass":"PAYMENT","channel":"IN_APP","enabled":false}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(put("/api/customer/notifications/preferences")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferences":[{"eventClass":"MARKETING","channel":"EMAIL","enabled":true}]}
                                """))
                .andExpect(status().isOk());

        var saved = preferenceRepository.findByUserIdAndEventClassAndChannel(
                7L, NotificationEventClass.MARKETING, NotificationChannel.EMAIL).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(saved.isEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(7L, saved.getUserId());
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

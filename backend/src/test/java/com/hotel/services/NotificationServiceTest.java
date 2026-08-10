package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.NotificationRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserPropertyRepository userPropertyRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, messagingTemplate, userPropertyRepository, 30);
    }

    @Test
    void systemNotificationUsesProtectedAdminTopic() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification saved = notificationService.sendSystemNotification("SYSTEM", "Title", "Message");

        verify(messagingTemplate).convertAndSend("/topic/admin/notifications", saved);
    }

    @Test
    void personalNotificationUsesStandardUserQueue() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification saved = notificationService.sendUserNotification(
                "staff-user", 7L, "SYSTEM", "Title", "Message");

        verify(messagingTemplate).convertAndSendToUser(
                "staff-user", "/queue/notifications", saved);
    }

    @Test
    void propertyNotificationTargetsAssignedStaffAndExcludesSystemAdministrators() {
        User receptionist = user(7L, "reception", "RECEPTIONIST");
        User systemAdmin = user(1L, "admin", "SUPER_ADMIN");
        UserProperty receptionistAssignment = assignment(receptionist);
        UserProperty adminAssignment = assignment(systemAdmin);
        when(userPropertyRepository.findByHotelId(9L))
                .thenReturn(List.of(receptionistAssignment, adminAssignment));
        when(notificationRepository.findByEventKey("reservation-created:20:user:7"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Notification> sent = notificationService.sendPropertyNotification(
                9L, "reservation-created:20", "BOOKING", "New booking", "Message");

        assertEquals(1, sent.size());
        assertEquals(7L, sent.get(0).getUserId());
        verify(messagingTemplate).convertAndSendToUser(
                eq("reception"), eq("/queue/notifications"), any(Notification.class));
        verify(messagingTemplate, never()).convertAndSendToUser(
                eq("admin"), eq("/queue/notifications"), any(Notification.class));
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/admin/notifications"), any(Notification.class));
    }

    @Test
    void idempotentPersonalNotificationDoesNotPersistOrPushAReplay() {
        Notification existing = new Notification();
        existing.setEventKey("refund:event:1");
        when(notificationRepository.findByEventKey("refund:event:1"))
                .thenReturn(java.util.Optional.empty(), java.util.Optional.of(existing));
        when(notificationRepository.save(any(Notification.class))).thenReturn(existing);

        notificationService.sendUserNotificationOnce(
                "refund:event:1", "staff-user", 7L, "PAYMENT", "Title", "Message");
        notificationService.sendUserNotificationOnce(
                "refund:event:1", "staff-user", 7L, "PAYMENT", "Title", "Message");

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                "staff-user", "/queue/notifications", existing);
        verify(messagingTemplate, never()).convertAndSend("/topic/admin/notifications", existing);
    }

    @Test
    void historyClampsPagingAndReportsRetainedUnreadCount() {
        Notification notification = notification(11L, false);
        when(notificationRepository.findVisibleToUser(eq(11L), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(notificationRepository.countUnreadVisibleToUser(eq(11L), any(LocalDateTime.class)))
                .thenReturn(4L);

        NotificationService.NotificationHistoryPage result =
                notificationService.getNotificationHistory(11L, -2, 500);

        assertEquals(List.of(notification), result.content());
        assertEquals(4L, result.unreadCount());
        assertEquals(30, result.retentionDays());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findVisibleToUser(
                eq(11L), any(LocalDateTime.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                pageable.getSort().getOrderFor("createdAt").getDirection());
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                pageable.getSort().getOrderFor("id").getDirection());
    }

    @Test
    void missingOrExpiredNotificationUsesStableNotFoundException() {
        when(notificationRepository.findByIdAndCreatedAtGreaterThanEqual(
                eq(99L), any(LocalDateTime.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markAsRead(99L, 11L));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void actorCannotMarkAnotherUsersNotification() {
        Notification foreign = notification(22L, false);
        when(notificationRepository.findByIdAndCreatedAtGreaterThanEqual(
                eq(3L), any(LocalDateTime.class))).thenReturn(Optional.of(foreign));

        assertThrows(AccessDeniedException.class,
                () -> notificationService.markAsRead(3L, 11L));
        assertFalse(foreign.isRead());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markingOwnedNotificationIsIdempotent() {
        Notification owned = notification(11L, false);
        when(notificationRepository.findByIdAndCreatedAtGreaterThanEqual(
                eq(3L), any(LocalDateTime.class))).thenReturn(Optional.of(owned));

        notificationService.markAsRead(3L, 11L);
        notificationService.markAsRead(3L, 11L);

        assertTrue(owned.isRead());
        verify(notificationRepository, times(1)).save(owned);
    }

    private Notification notification(Long userId, boolean read) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("SYSTEM");
        notification.setTitle("Title");
        notification.setMessage("Message");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(read);
        return notification;
    }

    private User user(Long id, String username, String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRoles(Set.of(role));
        return user;
    }

    private UserProperty assignment(User user) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(user);
        assignment.setStatus("ACTIVE");
        return assignment;
    }
}

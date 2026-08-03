package com.hotel.services;

import com.hotel.entities.Notification;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerNotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private CustomerNotificationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerNotificationService(notificationRepository, 365);
    }

    @Test
    void mapsOwnInboxToPrivacySafeRowsWithDeepLinksAndUnreadCount() {
        Notification booking = notification(11L, 7L, "BOOKING", false);
        Notification invoice = notification(12L, 7L, "INVOICE", true);
        when(notificationRepository.findCustomerHistory(
                eq(7L), eq(false), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking, invoice)));
        when(notificationRepository.countActiveUnread(eq(7L), any(LocalDateTime.class))).thenReturn(1L);

        CustomerNotificationService.CustomerNotificationPage result = service.getInbox(7L, 0, 20, false);

        assertEquals(2, result.content().size());
        assertEquals("/booking-history", result.content().get(0).deepLink());
        assertEquals("/my-invoices", result.content().get(1).deepLink());
        assertEquals(1, result.unreadCount());
    }

    @Test
    void marksOnlyAnOwnedNotificationAsRead() {
        Notification owned = notification(11L, 7L, "REFUND", false);
        when(notificationRepository.findByIdAndUserIdAndCreatedAtGreaterThanEqual(
                eq(11L), eq(7L), any(LocalDateTime.class))).thenReturn(Optional.of(owned));
        when(notificationRepository.save(owned)).thenReturn(owned);

        var result = service.markAsRead(11L, 7L);

        assertTrue(result.isRead());
        assertEquals("/refunds", result.deepLink());
        verify(notificationRepository).save(owned);
    }

    @Test
    void returnsNotFoundWithoutSavingWhenTheRowIsNotOwned() {
        when(notificationRepository.findByIdAndUserIdAndCreatedAtGreaterThanEqual(
                eq(99L), eq(7L), any(LocalDateTime.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markAsRead(99L, 7L));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void archivesAndRestoresOnlyAnOwnedRetainedNotification() {
        Notification owned = notification(13L, 7L, "BOOKING", false);
        when(notificationRepository.findByIdAndUserIdAndCreatedAtGreaterThanEqual(
                eq(13L), eq(7L), any(LocalDateTime.class))).thenReturn(Optional.of(owned));
        when(notificationRepository.save(owned)).thenReturn(owned);

        var archived = service.archive(13L, 7L);
        assertTrue(archived.archivedAt() != null);

        var restored = service.restore(13L, 7L);
        assertEquals(null, restored.archivedAt());
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(owned);
    }

    @Test
    void foreignArchiveUsesTheSamePrivacySafeNotFound() {
        when(notificationRepository.findByIdAndUserIdAndCreatedAtGreaterThanEqual(
                eq(88L), eq(7L), any(LocalDateTime.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.archive(88L, 7L));
        verify(notificationRepository, never()).save(any());
    }

    private Notification notification(Long id, Long userId, String type, boolean read) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(type + " title");
        notification.setMessage(type + " message");
        notification.setRead(read);
        notification.setCreatedAt(LocalDateTime.of(2026, 8, 4, 10, 0));
        return notification;
    }
}

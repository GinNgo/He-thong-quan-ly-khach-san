package com.hotel.services;

import com.hotel.dtos.PropertyGalleryOrderRequest;
import com.hotel.dtos.PropertyImageLinkRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyImage;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyGalleryServiceTest {

    @Mock private HotelRepository hotelRepository;
    @Mock private PropertyImageRepository propertyImageRepository;
    @Mock private RoomTypeImageRepository roomTypeImageRepository;
    @Mock private RoomImageRepository roomImageRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private SubscriptionFeatureService subscriptionFeatureService;
    @Mock private FileUploadService fileUploadService;

    private PropertyGalleryService service;

    @BeforeEach
    void setUp() {
        service = new PropertyGalleryService(
                hotelRepository,
                propertyImageRepository,
                roomTypeImageRepository,
                roomImageRepository,
                propertyAccessService,
                subscriptionFeatureService,
                fileUploadService);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void linkLocksPropertyBeforeAggregateQuotaAndCreatesOnlyPrimaryImage() {
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(roomTypeImageRepository.countByRoomTypeHotelId(10L)).thenReturn(2L);
        when(roomImageRepository.countByRoomHotelId(10L)).thenReturn(3L);
        when(propertyImageRepository.saveAndFlush(any(PropertyImage.class))).thenAnswer(invocation -> {
            PropertyImage image = invocation.getArgument(0);
            image.setId(91L);
            return image;
        });

        var result = service.addLink(10L, new PropertyImageLinkRequest(
                "https://cdn.example/property.jpg", "Sanh chinh", "Main lobby", false));

        assertEquals(91L, result.id());
        assertTrue(result.primary());
        assertEquals("https://cdn.example/property.jpg", hotel.getMainImage());
        InOrder order = inOrder(hotelRepository, propertyImageRepository, roomTypeImageRepository,
                roomImageRepository, subscriptionFeatureService);
        order.verify(hotelRepository).findByIdForUpdate(10L);
        order.verify(propertyImageRepository).findByHotelIdOrderBySortOrderAsc(10L);
        order.verify(roomTypeImageRepository).countByRoomTypeHotelId(10L);
        order.verify(roomImageRepository).countByRoomHotelId(10L);
        order.verify(subscriptionFeatureService)
                .checkFeatureLimitForProperty(10L, "MAX_IMAGES", 5L, 1L);
        order.verify(propertyImageRepository).saveAndFlush(any(PropertyImage.class));
    }

    @Test
    void quotaRejectionLeavesGalleryAndStorageUntouched() {
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L))
                .thenReturn(List.of(image(1L, hotel, true, 0, "https://cdn.example/one.jpg")));
        when(roomTypeImageRepository.countByRoomTypeHotelId(10L)).thenReturn(4L);
        when(roomImageRepository.countByRoomHotelId(10L)).thenReturn(5L);
        org.mockito.Mockito.doThrow(new IllegalStateException("limit"))
                .when(subscriptionFeatureService)
                .checkFeatureLimitForProperty(10L, "MAX_IMAGES", 10L, 1L);

        assertThrows(IllegalStateException.class, () -> service.upload(
                10L, new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1}),
                null, null, false));

        verify(fileUploadService, never()).storePropertyImage(any(), any());
        verify(propertyImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignedUserReceivesNotFoundForCrossPropertyMutation() {
        Hotel hotel = hotel(10L);
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(20L));

        assertThrows(ResourceNotFoundException.class, () -> service.addLink(
                10L, new PropertyImageLinkRequest("https://cdn.example/hidden.jpg", null, null, false)));

        verify(propertyImageRepository, never()).findByHotelIdOrderBySortOrderAsc(any());
        verify(subscriptionFeatureService, never())
                .checkFeatureLimitForProperty(any(), any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void linkEndpointCannotClaimAnExistingManagedUpload() {
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.addLink(
                10L,
                new PropertyImageLinkRequest(
                        "/api/public/uploads/avatar-99-secret.png", null, null, false)));

        verify(propertyImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void foreignImageIdIsHiddenAsNotFound() {
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L))
                .thenReturn(List.of(image(1L, hotel, true, 0, "https://cdn.example/one.jpg")));

        assertThrows(ResourceNotFoundException.class, () -> service.setPrimary(10L, 999L));

        verify(propertyImageRepository, never()).saveAll(any());
    }

    @Test
    void failedDatabaseWriteImmediatelyDeletesNewManagedUpload() {
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(fileUploadService.storePropertyImage(eq(10L), any()))
                .thenReturn(new FileUploadService.StoredImage(
                        "/api/public/uploads/property-10-new.png", "image/png", 20, 20));
        when(propertyImageRepository.saveAndFlush(any(PropertyImage.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.upload(
                10L, new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1}),
                null, null, false));

        verify(fileUploadService).deleteManagedImage("/api/public/uploads/property-10-new.png");
    }

    @Test
    void transactionRollbackDeletesUploadThatWasAlreadyFlushed() {
        TransactionSynchronizationManager.initSynchronization();
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(fileUploadService.storePropertyImage(eq(10L), any()))
                .thenReturn(new FileUploadService.StoredImage(
                        "/api/public/uploads/property-10-new.png", "image/png", 20, 20));
        when(propertyImageRepository.saveAndFlush(any(PropertyImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(10L, new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1}), null, null, false);
        verify(fileUploadService, never()).deleteManagedImage(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(fileUploadService).deleteManagedImage("/api/public/uploads/property-10-new.png");
    }

    @Test
    void deletingPrimaryCompactsOrderPromotesNextAndCleansManagedFileAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        Hotel hotel = hotel(10L);
        PropertyImage first = image(1L, hotel, true, 0, "/api/public/uploads/property-10-old.png");
        PropertyImage second = image(2L, hotel, false, 4, "https://cdn.example/two.jpg");
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L))
                .thenReturn(List.of(first, second));

        var remaining = service.delete(10L, 1L);

        assertEquals(1, remaining.size());
        assertEquals(0, remaining.getFirst().sortOrder());
        assertTrue(remaining.getFirst().primary());
        assertEquals("https://cdn.example/two.jpg", hotel.getMainImage());
        verify(fileUploadService, never()).deleteManagedImage(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(fileUploadService).deleteManagedImage("/api/public/uploads/property-10-old.png");
    }

    @Test
    void reorderRequiresEveryCurrentImageExactlyOnceAndPreservesPrimary() {
        Hotel hotel = hotel(10L);
        PropertyImage first = image(1L, hotel, true, 0, "https://cdn.example/one.jpg");
        PropertyImage second = image(2L, hotel, false, 1, "https://cdn.example/two.jpg");
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L))
                .thenReturn(List.of(first, second));

        var reordered = service.reorder(10L, new PropertyGalleryOrderRequest(List.of(2L, 1L)));

        assertEquals(List.of(2L, 1L), reordered.stream().map(item -> item.id()).toList());
        assertFalse(reordered.getFirst().primary());
        assertTrue(reordered.get(1).primary());
        assertEquals("https://cdn.example/one.jpg", hotel.getMainImage());

        assertThrows(ResourceNotFoundException.class, () -> service.reorder(
                10L, new PropertyGalleryOrderRequest(List.of(2L, 999L))));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(
                10L, new PropertyGalleryOrderRequest(List.of(1L, 1L))));
        verify(propertyImageRepository, times(1)).saveAll(any());
    }

    private void allowAdminMutation(Hotel hotel) {
        when(hotelRepository.findByIdForUpdate(hotel.getId())).thenReturn(Optional.of(hotel));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private PropertyImage image(Long id, Hotel hotel, boolean primary, int sortOrder, String url) {
        PropertyImage image = new PropertyImage();
        image.setId(id);
        image.setHotel(hotel);
        image.setImageUrl(url);
        image.setIsPrimary(primary);
        image.setSortOrder(sortOrder);
        image.setIsDemo(false);
        return image;
    }
}

package com.hotel.services;

import com.hotel.dtos.PropertyGalleryOrderRequest;
import com.hotel.dtos.PropertyImageLinkRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyImage;
import com.hotel.entities.PropertyMedia;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private PropertyMediaService propertyMediaService;
    @Mock private PropertyMediaPolicy propertyMediaPolicy;

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
                propertyMediaService,
                propertyMediaPolicy);
    }

    @Test
    void linkLocksPropertyBeforeAggregateQuotaAndCreatesOwnedPrimaryImage() {
        Hotel hotel = hotel(10L);
        PropertyMedia media = media(81L, hotel, "https://cdn.example/property.jpg", "EXTERNAL_HTTPS");
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(roomTypeImageRepository.countByRoomTypeHotelId(10L)).thenReturn(2L);
        when(roomImageRepository.countByRoomHotelId(10L)).thenReturn(3L);
        when(propertyMediaPolicy.normalizeExternalUrl("https://cdn.example/property.jpg"))
                .thenReturn("https://cdn.example/property.jpg");
        when(propertyMediaService.createExternal(
                hotel, "https://cdn.example/property.jpg", "Sanh chinh", "Main lobby"))
                .thenReturn(media);
        when(propertyImageRepository.saveAndFlush(any(PropertyImage.class))).thenAnswer(invocation -> {
            PropertyImage image = invocation.getArgument(0);
            image.setId(91L);
            return image;
        });

        var result = service.addLink(10L, new PropertyImageLinkRequest(
                "https://cdn.example/property.jpg", "Sanh chinh", "Main lobby", false));

        assertEquals(91L, result.id());
        assertEquals(81L, result.mediaId());
        assertTrue(result.primary());
        assertEquals("https://cdn.example/property.jpg", hotel.getMainImage());
        InOrder order = inOrder(hotelRepository, propertyImageRepository, roomTypeImageRepository,
                roomImageRepository, subscriptionFeatureService, propertyMediaService);
        order.verify(hotelRepository).findByIdForUpdate(10L);
        order.verify(propertyImageRepository).findByHotelIdOrderBySortOrderAsc(10L);
        order.verify(roomTypeImageRepository).countByRoomTypeHotelId(10L);
        order.verify(roomImageRepository).countByRoomHotelId(10L);
        order.verify(subscriptionFeatureService)
                .checkFeatureLimitForProperty(10L, "MAX_IMAGES", 5L, 1L);
        order.verify(propertyMediaService).createExternal(
                hotel, "https://cdn.example/property.jpg", "Sanh chinh", "Main lobby");
        order.verify(propertyImageRepository).saveAndFlush(any(PropertyImage.class));
    }

    @Test
    void quotaRejectionDoesNotCreateOwnedMedia() {
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L))
                .thenReturn(List.of(image(1L, hotel, media(1L, hotel, "https://cdn.example/one.jpg", "EXTERNAL_HTTPS"), true, 0)));
        when(roomTypeImageRepository.countByRoomTypeHotelId(10L)).thenReturn(4L);
        when(roomImageRepository.countByRoomHotelId(10L)).thenReturn(5L);
        org.mockito.Mockito.doThrow(new IllegalStateException("limit"))
                .when(subscriptionFeatureService)
                .checkFeatureLimitForProperty(10L, "MAX_IMAGES", 10L, 1L);

        assertThrows(IllegalStateException.class, () -> service.upload(
                10L, new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1}),
                "Phong", null, false));

        verify(propertyMediaService, never()).createUpload(any(), any(), any(), any());
        verify(propertyImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignedUserReceivesNotFoundForCrossPropertyMutation() {
        Hotel hotel = hotel(10L);
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(20L));

        assertThrows(ResourceNotFoundException.class, () -> service.addLink(
                10L, new PropertyImageLinkRequest(
                        "https://cdn.example/hidden.jpg", "Anh an", null, false)));

        verify(propertyImageRepository, never()).findByHotelIdOrderBySortOrderAsc(any());
        verify(propertyMediaService, never()).createExternal(any(), any(), any(), any());
    }

    @Test
    void managedOrInsecureLinksAreRejectedBeforeCreatingMedia() {
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(propertyMediaPolicy.normalizeExternalUrl(any())).thenThrow(
                new IllegalArgumentException("External image URLs must use HTTPS."));

        assertThrows(IllegalArgumentException.class, () -> service.addLink(
                10L,
                new PropertyImageLinkRequest(
                        "/api/public/uploads/avatar-99-secret.png", "Anh", null, false)));

        verify(propertyMediaService, never()).createExternal(any(), any(), any(), any());
        verify(propertyImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void foreignImageIdIsHiddenAsNotFound() {
        Hotel hotel = hotel(10L);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L))
                .thenReturn(List.of(image(1L, hotel, media(1L, hotel, "https://cdn.example/one.jpg", "EXTERNAL_HTTPS"), true, 0)));

        assertThrows(ResourceNotFoundException.class, () -> service.setPrimary(10L, 999L));

        verify(propertyImageRepository, never()).saveAll(any());
    }

    @Test
    void failedAssociationDiscardsNewOwnedMediaOutsideTransactionHarness() {
        Hotel hotel = hotel(10L);
        PropertyMedia media = media(81L, hotel, "/api/public/uploads/property-10-new.png", "MANAGED_UPLOAD");
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(propertyMediaService.createUpload(eq(hotel), any(), eq("Phong"), isNull()))
                .thenReturn(media);
        when(propertyImageRepository.saveAndFlush(any(PropertyImage.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.upload(
                10L, new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1}),
                "Phong", null, false));

        verify(propertyMediaService).discardAfterFailedAssociation(media);
    }

    @Test
    void deletingPrimaryCompactsOrderPromotesNextAndReleasesMedia() {
        Hotel hotel = hotel(10L);
        PropertyMedia firstMedia = media(1L, hotel, "/api/public/uploads/property-10-old.png", "MANAGED_UPLOAD");
        PropertyMedia secondMedia = media(2L, hotel, "https://cdn.example/two.jpg", "EXTERNAL_HTTPS");
        PropertyImage first = image(1L, hotel, firstMedia, true, 0);
        PropertyImage second = image(2L, hotel, secondMedia, false, 4);
        allowAdminMutation(hotel);
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L))
                .thenReturn(List.of(first, second));

        var remaining = service.delete(10L, 1L);

        assertEquals(1, remaining.size());
        assertEquals(0, remaining.getFirst().sortOrder());
        assertTrue(remaining.getFirst().primary());
        assertEquals("https://cdn.example/two.jpg", hotel.getMainImage());
        verify(propertyImageRepository).flush();
        verify(propertyMediaService).releaseIfUnreferenced(firstMedia);
    }

    @Test
    void reorderRequiresEveryCurrentImageExactlyOnceAndPreservesPrimary() {
        Hotel hotel = hotel(10L);
        PropertyImage first = image(1L, hotel, media(1L, hotel, "https://cdn.example/one.jpg", "EXTERNAL_HTTPS"), true, 0);
        PropertyImage second = image(2L, hotel, media(2L, hotel, "https://cdn.example/two.jpg", "EXTERNAL_HTTPS"), false, 1);
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

    private PropertyMedia media(Long id, Hotel hotel, String url, String sourceType) {
        PropertyMedia media = new PropertyMedia();
        media.setId(id);
        media.setHotel(hotel);
        media.setPublicUrl(url);
        media.setSourceType(sourceType);
        media.setAltTextVi("Anh");
        media.setStatus("ACTIVE");
        return media;
    }

    private PropertyImage image(
            Long id,
            Hotel hotel,
            PropertyMedia media,
            boolean primary,
            int sortOrder) {
        PropertyImage image = new PropertyImage();
        image.setId(id);
        image.setHotel(hotel);
        image.setMedia(media);
        image.setImageUrl(media.getPublicUrl());
        image.setAltTextVi(media.getAltTextVi());
        image.setIsPrimary(primary);
        image.setSortOrder(sortOrder);
        image.setIsDemo(false);
        return image;
    }
}

package com.hotel.services;

import com.hotel.dtos.RoomTypeGalleryOrderRequest;
import com.hotel.dtos.RoomTypeImageLinkRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyMedia;
import com.hotel.entities.RoomType;
import com.hotel.entities.RoomTypeImage;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomTypeGalleryServiceTest {
    @Mock HotelRepository hotelRepository;
    @Mock RoomTypeRepository roomTypeRepository;
    @Mock RoomTypeImageRepository roomTypeImageRepository;
    @Mock PropertyImageRepository propertyImageRepository;
    @Mock RoomImageRepository roomImageRepository;
    @Mock PropertyAccessService propertyAccessService;
    @Mock SubscriptionFeatureService subscriptionFeatureService;
    @Mock PropertyMediaService propertyMediaService;
    @Mock PropertyMediaPolicy propertyMediaPolicy;
    RoomTypeGalleryService service;
    Hotel hotel;
    RoomType roomType;

    @BeforeEach void setUp() {
        service = new RoomTypeGalleryService(hotelRepository, roomTypeRepository, roomTypeImageRepository,
                propertyImageRepository, roomImageRepository, propertyAccessService,
                subscriptionFeatureService, propertyMediaService, propertyMediaPolicy);
        hotel = new Hotel(); hotel.setId(10L); hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED"); hotel.setOperationStatus("ACTIVE");
        roomType = new RoomType(); roomType.setId(20L); roomType.setHotel(hotel);
    }

    @Test void mutationLocksHotelBeforeRoomTypeAndChecksAggregateQuota() {
        allowAdminMutation();
        when(propertyImageRepository.countByHotelId(10L)).thenReturn(2L);
        when(roomTypeImageRepository.countByRoomTypeHotelId(10L)).thenReturn(3L);
        when(roomImageRepository.countByRoomHotelId(10L)).thenReturn(4L);
        when(propertyMediaPolicy.normalizeExternalUrl("https://cdn.example/deluxe.jpg"))
                .thenReturn("https://cdn.example/deluxe.jpg");
        PropertyMedia media = media(30L, "https://cdn.example/deluxe.jpg", "EXTERNAL_HTTPS");
        when(propertyMediaService.createExternal(hotel, media.getPublicUrl(), "Phong deluxe", "Deluxe room"))
                .thenReturn(media);
        when(roomTypeImageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            RoomTypeImage image = invocation.getArgument(0); image.setId(40L); return image;
        });

        var result = service.addLink(20L, new RoomTypeImageLinkRequest(
                media.getPublicUrl(), "Phong deluxe", "Deluxe room", false));

        assertTrue(result.primary());
        assertEquals(0, result.sortOrder());
        InOrder order = inOrder(hotelRepository, roomTypeRepository, subscriptionFeatureService, propertyMediaService);
        order.verify(roomTypeRepository).findById(20L);
        order.verify(hotelRepository).findByIdForUpdate(10L);
        order.verify(roomTypeRepository).findByIdForUpdate(20L);
        order.verify(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_IMAGES", 9L, 1L);
        order.verify(propertyMediaService).createExternal(hotel, media.getPublicUrl(), "Phong deluxe", "Deluxe room");
    }

    @Test void crossTenantMutationIsHiddenBeforeAnyLockOrMediaCreation() {
        when(roomTypeRepository.findById(20L)).thenReturn(Optional.of(roomType));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(99L));

        assertThrows(ResourceNotFoundException.class, () -> service.addLink(20L,
                new RoomTypeImageLinkRequest("https://cdn.example/hidden.jpg", "Anh", null, false)));

        verify(hotelRepository, never()).findByIdForUpdate(any());
        verify(propertyMediaService, never()).createExternal(any(), any(), any(), any());
    }

    @Test void failedImageAssociationDiscardsNewOwnedMedia() {
        RoomTypeImage existing = image(1L, media(29L, "https://cdn.example/old.jpg", "EXTERNAL_HTTPS"), true, 0);
        allowAdminMutation(List.of(existing));
        PropertyMedia media = media(30L, "https://cdn.example/new.jpg", "EXTERNAL_HTTPS");
        when(propertyMediaPolicy.normalizeExternalUrl(media.getPublicUrl())).thenReturn(media.getPublicUrl());
        when(propertyMediaService.createExternal(hotel, media.getPublicUrl(), "Anh moi", null)).thenReturn(media);
        when(roomTypeImageRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.addLink(20L,
                new RoomTypeImageLinkRequest(media.getPublicUrl(), "Anh moi", null, false)));

        verify(propertyMediaService).discardAfterFailedAssociation(media);
        verify(roomTypeImageRepository, never()).delete(any());
        verify(roomTypeImageRepository, never()).deleteByRoomTypeId(any());
        assertEquals("https://cdn.example/old.jpg", existing.getImageUrl());
        assertTrue(existing.getIsPrimary());
    }

    @Test void reorderUsesCollisionSafeTwoPhaseWriteAndPreservesPrimary() {
        RoomTypeImage first = image(1L, media(31L, "https://cdn.example/one.jpg", "EXTERNAL_HTTPS"), true, 0);
        RoomTypeImage second = image(2L, media(32L, "https://cdn.example/two.jpg", "EXTERNAL_HTTPS"), false, 1);
        allowAdminMutation(List.of(first, second));

        var result = service.reorder(20L, new RoomTypeGalleryOrderRequest(List.of(2L, 1L)));

        assertEquals(List.of(2L, 1L), result.stream().map(item -> item.id()).toList());
        assertEquals(List.of(0, 1), result.stream().map(item -> item.sortOrder()).toList());
        assertTrue(result.get(1).primary());
        verify(roomTypeImageRepository).saveAllAndFlush(any());
        verify(roomTypeImageRepository).saveAll(any());
    }

    @Test void deletingPrimaryPromotesNextCompactsOrderAndReleasesMedia() {
        PropertyMedia oldMedia = media(31L, "/api/public/uploads/property-10-old.png", "MANAGED_UPLOAD");
        RoomTypeImage first = image(1L, oldMedia, true, 0);
        RoomTypeImage second = image(2L, media(32L, "https://cdn.example/two.jpg", "EXTERNAL_HTTPS"), false, 4);
        allowAdminMutation(List.of(first, second));

        var remaining = service.delete(20L, 1L);

        assertEquals(1, remaining.size());
        assertEquals(0, remaining.getFirst().sortOrder());
        assertTrue(remaining.getFirst().primary());
        verify(roomTypeImageRepository).flush();
        verify(propertyMediaService).releaseIfUnreferenced(oldMedia);
    }

    @Test void duplicateOrForeignImageIdsCannotMutateGallery() {
        RoomTypeImage first = image(1L, media(31L, "https://cdn.example/one.jpg", "EXTERNAL_HTTPS"), true, 0);
        RoomTypeImage second = image(2L, media(32L, "https://cdn.example/two.jpg", "EXTERNAL_HTTPS"), false, 1);
        allowAdminMutation(List.of(first, second));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(20L, new RoomTypeGalleryOrderRequest(List.of(1L, 1L))));
        assertThrows(ResourceNotFoundException.class, () -> service.setPrimary(20L, 999L));
    }

    private void allowAdminMutation() { allowAdminMutation(List.of()); }
    private void allowAdminMutation(List<RoomTypeImage> images) {
        when(roomTypeRepository.findById(20L)).thenReturn(Optional.of(roomType));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(roomType));
        when(roomTypeImageRepository.findByRoomTypeIdOrderBySortOrderAsc(20L)).thenReturn(images);
    }
    private PropertyMedia media(Long id, String url, String source) {
        PropertyMedia media = new PropertyMedia(); media.setId(id); media.setHotel(hotel);
        media.setPublicUrl(url); media.setSourceType(source); media.setAltTextVi("Anh"); media.setStatus("ACTIVE"); return media;
    }
    private RoomTypeImage image(Long id, PropertyMedia media, boolean primary, int order) {
        RoomTypeImage image = new RoomTypeImage(); image.setId(id); image.setRoomType(roomType); image.setMedia(media);
        image.setImageUrl(media.getPublicUrl()); image.setAltTextVi(media.getAltTextVi());
        image.setIsPrimary(primary); image.setSortOrder(order); image.setIsDemo(false); return image;
    }
}

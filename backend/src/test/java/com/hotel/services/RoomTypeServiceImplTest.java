package com.hotel.services;

import com.hotel.dtos.RoomTypeDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyMedia;
import com.hotel.entities.RoomType;
import com.hotel.entities.RoomTypeImage;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomTypeServiceImplTest {
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomAvailabilityService roomAvailabilityService;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeImageRepository roomTypeImageRepository;
    @Mock private PropertyImageRepository propertyImageRepository;
    @Mock private RoomImageRepository roomImageRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private SubscriptionFeatureService subscriptionFeatureService;
    @Mock private PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;
    @Mock private PropertyMediaService propertyMediaService;

    @InjectMocks
    private RoomTypeServiceImpl roomTypeService;

    private Hotel hotel;

    @BeforeEach
    void setUp() {
        hotel = new Hotel();
        hotel.setId(10L);
    }

    @Test
    void createRoomType_WhenDirectRouteExceedsQuota_DoesNotPersist() {
        RoomTypeDTO request = validRequest();
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(hotel);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roomTypeRepository.countByHotelId(10L)).thenReturn(2L);
        doThrow(new RuntimeException("quota exceeded"))
                .when(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_ROOM_TYPES", 2L, 1L);

        assertThrows(RuntimeException.class, () -> roomTypeService.createRoomType(request));

        verify(roomTypeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRoomType_WhenReplacementImagesExceedQuota_DoesNotPersist() {
        RoomType existing = new RoomType();
        existing.setId(20L);
        existing.setHotel(hotel);
        existing.setCode("DELUXE");
        RoomTypeDTO request = validRequest();
        request.setImageUrls(List.of("/1.jpg", "/2.jpg", "/3.jpg"));

        RoomTypeImage currentImage = new RoomTypeImage();
        when(roomTypeRepository.findById(20L)).thenReturn(Optional.of(existing));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roomTypeImageRepository.findByRoomTypeIdOrderBySortOrderAsc(20L)).thenReturn(List.of(currentImage));
        when(propertyImageRepository.countByHotelId(10L)).thenReturn(1L);
        when(roomTypeImageRepository.countByRoomTypeHotelId(10L)).thenReturn(2L);
        when(roomImageRepository.countByRoomHotelId(10L)).thenReturn(1L);
        doThrow(new RuntimeException("image quota exceeded"))
                .when(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_IMAGES", 3L, 3L);

        assertThrows(RuntimeException.class, () -> roomTypeService.updateRoomType(20L, request));

        verify(roomTypeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRoomTypeReplacesGalleryWithOwnedMediaAndReleasesOldReferences() {
        RoomType existing = new RoomType();
        existing.setId(20L);
        existing.setHotel(hotel);
        existing.setCode("DELUXE");
        existing.setNameVi("Deluxe cũ");
        PropertyMedia oldMedia = new PropertyMedia();
        oldMedia.setId(31L);
        oldMedia.setHotel(hotel);
        RoomTypeImage oldImage = new RoomTypeImage();
        oldImage.setMedia(oldMedia);
        oldImage.setImageUrl("https://cdn.example.com/old.jpg");

        RoomTypeDTO request = validRequest();
        request.setImageUrls(List.of("https://cdn.example.com/new.jpg"));
        PropertyMedia newMedia = new PropertyMedia();
        newMedia.setId(32L);
        newMedia.setHotel(hotel);
        newMedia.setSourceType("EXTERNAL_HTTPS");
        newMedia.setPublicUrl("https://cdn.example.com/new.jpg");
        newMedia.setAltTextVi("Deluxe");

        when(roomTypeRepository.findById(20L)).thenReturn(Optional.of(existing));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(roomTypeRepository.findByCodeAndHotelId("DELUXE", 10L)).thenReturn(Optional.empty());
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roomTypeImageRepository.findByRoomTypeIdOrderBySortOrderAsc(20L)).thenReturn(List.of(oldImage));
        when(propertyMediaService.createExternal(
                hotel, "https://cdn.example.com/new.jpg", "Deluxe", null)).thenReturn(newMedia);

        roomTypeService.updateRoomType(20L, request);

        ArgumentCaptor<RoomTypeImage> imageCaptor = ArgumentCaptor.forClass(RoomTypeImage.class);
        verify(roomTypeImageRepository).deleteByRoomTypeId(20L);
        verify(roomTypeImageRepository).save(imageCaptor.capture());
        verify(roomTypeImageRepository).flush();
        verify(propertyMediaService).releaseIfUnreferenced(oldMedia);
        RoomTypeImage savedImage = imageCaptor.getValue();
        assertSame(newMedia, savedImage.getMedia());
        assertEquals(true, savedImage.getIsPrimary());
        assertEquals(0, savedImage.getSortOrder());
        assertEquals("Deluxe", savedImage.getAltTextVi());
    }

    private RoomTypeDTO validRequest() {
        RoomTypeDTO request = new RoomTypeDTO();
        request.setHotelId(10L);
        request.setCode("DELUXE");
        request.setNameVi("Deluxe");
        request.setBasePrice(new BigDecimal("1200000"));
        request.setMaxGuests(2);
        return request;
    }
}

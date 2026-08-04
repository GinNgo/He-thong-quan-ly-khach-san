package com.hotel.services;

import com.hotel.dtos.BulkRoomRequest;
import com.hotel.dtos.RoomDTO;
import com.hotel.dtos.RoomImageDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyMedia;
import com.hotel.entities.Room;
import com.hotel.entities.RoomImage;
import com.hotel.entities.RoomType;
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

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {
    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomImageRepository roomImageRepository;
    @Mock private PropertyImageRepository propertyImageRepository;
    @Mock private RoomTypeImageRepository roomTypeImageRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private SubscriptionFeatureService subscriptionFeatureService;
    @Mock private PropertyMediaService propertyMediaService;
    @Mock private PropertyMediaPolicy propertyMediaPolicy;

    @InjectMocks
    private RoomServiceImpl roomService;

    private Hotel hotel;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        hotel = new Hotel();
        hotel.setId(10L);
        roomType = new RoomType();
        roomType.setId(20L);
        roomType.setHotel(hotel);
    }

    @Test
    void createRoom_WhenDirectRouteExceedsQuota_DoesNotPersist() {
        RoomDTO request = new RoomDTO();
        request.setRoomTypeId(20L);
        request.setRoomNumber("101");

        when(roomTypeRepository.findById(20L)).thenReturn(Optional.of(roomType));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roomRepository.countByHotelId(10L)).thenReturn(5L);
        doThrow(new RuntimeException("quota exceeded"))
                .when(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_ROOMS", 5L, 1L);

        assertThrows(RuntimeException.class, () -> roomService.createRoom(request));

        verify(roomRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRoom_WhenSubscriptionExpired_PreservesReadOnlyAccess() {
        Room existing = new Room();
        existing.setId(30L);
        existing.setHotel(hotel);
        existing.setRoomType(roomType);
        existing.setStatus("AVAILABLE");
        existing.setHousekeepingStatus("CLEAN");
        existing.setMaintenanceStatus("NONE");
        RoomDTO request = new RoomDTO();
        request.setRoomTypeId(20L);

        when(roomRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(existing));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        doThrow(new RuntimeException("upgrade required"))
                .when(subscriptionFeatureService).requireFeatureForProperty(10L, "MAX_ROOMS");

        assertThrows(RuntimeException.class, () -> roomService.updateRoom(30L, request));

        verify(roomRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bulkCreate_WhenRequestedRangeExceedsQuota_DoesNotPersistPartialRows() {
        BulkRoomRequest request = new BulkRoomRequest();
        request.setRoomTypeId(20L);
        request.setHotelId(10L);
        request.setFromNumber(101);
        request.setToNumber(104);
        request.setFloor(1);

        when(roomTypeRepository.findById(20L)).thenReturn(Optional.of(roomType));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roomRepository.countByHotelId(10L)).thenReturn(3L);
        doThrow(new RuntimeException("quota exceeded"))
                .when(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_ROOMS", 3L, 4L);

        assertThrows(RuntimeException.class, () -> roomService.bulkCreate(request));

        verify(roomRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRoomLocksAggregateAndRejectsStateMutation() {
        Room existing = room("AVAILABLE", "CLEAN", "NONE");
        existing.setId(30L);
        existing.setHotel(hotel);
        existing.setRoomType(roomType);
        RoomDTO request = new RoomDTO();
        request.setRoomTypeId(20L);
        request.setRoomNumber("101");
        request.setFloor(1);
        request.setStatus("OCCUPIED");

        when(roomRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> roomService.updateRoom(30L, request));
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    void maintenanceCommandsUseLockedRoomAndPolicy() {
        Room existing = room("AVAILABLE", "CLEAN", "NONE");
        existing.setId(30L);
        existing.setHotel(hotel);
        existing.setRoomType(roomType);
        when(roomRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(existing));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RoomDTO started = roomService.startMaintenance(30L);
        assertEquals("MAINTENANCE", started.getStatus());
        RoomDTO completed = roomService.completeMaintenance(30L);
        assertEquals("AVAILABLE", completed.getStatus());
        verify(roomRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(30L);
    }

    @Test
    void createRoomAssociatesValidatedImagesWithPropertyOwnedMedia() {
        RoomImageDTO image = new RoomImageDTO();
        image.setImageUrl("https://cdn.example.com/rooms/101.jpg");
        image.setAltTextVi("Phòng 101");
        image.setAltTextEn("Room 101");
        RoomDTO request = new RoomDTO();
        request.setRoomTypeId(20L);
        request.setRoomNumber("101");
        request.setMaxGuests(2);
        request.setImages(List.of(image));

        PropertyMedia media = new PropertyMedia();
        media.setId(40L);
        media.setHotel(hotel);
        media.setSourceType("EXTERNAL_HTTPS");
        media.setPublicUrl(image.getImageUrl());
        media.setAltTextVi(image.getAltTextVi());
        media.setAltTextEn(image.getAltTextEn());

        when(roomTypeRepository.findById(20L)).thenReturn(Optional.of(roomType));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(roomRepository.findByHotelIdAndRoomNumber(10L, "101")).thenReturn(Optional.empty());
        when(propertyMediaPolicy.normalizeExternalUrl(image.getImageUrl())).thenReturn(image.getImageUrl());
        when(propertyMediaPolicy.requireAltTextVi(image.getAltTextVi())).thenReturn(image.getAltTextVi());
        when(propertyMediaPolicy.normalizeAltTextEn(image.getAltTextEn())).thenReturn(image.getAltTextEn());
        when(propertyMediaService.createExternal(hotel, image.getImageUrl(), image.getAltTextVi(), image.getAltTextEn()))
                .thenReturn(media);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room saved = invocation.getArgument(0);
            saved.setId(30L);
            return saved;
        });

        roomService.createRoom(request);

        ArgumentCaptor<RoomImage> imageCaptor = ArgumentCaptor.forClass(RoomImage.class);
        verify(roomImageRepository).save(imageCaptor.capture());
        RoomImage savedImage = imageCaptor.getValue();
        assertSame(media, savedImage.getMedia());
        assertEquals(0, savedImage.getSortOrder());
        assertEquals(true, savedImage.getIsPrimary());
        assertEquals("Phòng 101", savedImage.getAltTextVi());
    }

    private Room room(String status, String housekeeping, String maintenance) {
        Room room = new Room();
        room.setStatus(status);
        room.setHousekeepingStatus(housekeeping);
        room.setMaintenanceStatus(maintenance);
        room.setMaxGuests(2);
        room.setIsDemo(false);
        return room;
    }
}

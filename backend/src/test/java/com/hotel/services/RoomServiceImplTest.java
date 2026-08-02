package com.hotel.services;

import com.hotel.dtos.BulkRoomRequest;
import com.hotel.dtos.RoomDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Room;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
        RoomDTO request = new RoomDTO();
        request.setRoomTypeId(20L);

        when(roomRepository.findById(30L)).thenReturn(Optional.of(existing));
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
}

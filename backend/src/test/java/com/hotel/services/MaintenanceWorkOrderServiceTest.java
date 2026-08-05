package com.hotel.services;

import com.hotel.dtos.MaintenanceWorkOrderDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.MaintenanceWorkOrder;
import com.hotel.entities.Room;
import com.hotel.repositories.MaintenanceWorkOrderHistoryRepository;
import com.hotel.repositories.MaintenanceWorkOrderRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceWorkOrderServiceTest {
    @Mock MaintenanceWorkOrderRepository repository;
    @Mock MaintenanceWorkOrderHistoryRepository historyRepository;
    @Mock RoomRepository roomRepository;
    @Mock ReservationRoomRepository reservationRoomRepository;
    @Mock PropertyAccessService propertyAccessService;
    @Mock UserPropertyRepository userPropertyRepository;
    MaintenanceWorkOrderService service;
    Room room;

    @BeforeEach
    void setUp() {
        service = new MaintenanceWorkOrderService(repository, historyRepository, roomRepository,
                reservationRoomRepository, propertyAccessService, userPropertyRepository);
        Hotel hotel = new Hotel(); hotel.setId(7L);
        room = new Room(); room.setId(11L); room.setHotel(hotel); room.setRoomNumber("101");
        RoomStatePolicy.initialize(room);
        lenient().when(historyRepository.findByWorkOrderIdOrderByIdAsc(anyLong())).thenReturn(List.of());
        lenient().when(repository.save(any())).thenAnswer(invocation -> {
            MaintenanceWorkOrder value = invocation.getArgument(0); if (value.getId() == null) value.setId(41L); return value;
        });
    }

    @Test
    void createsTenantScopedWorkOrderWithBookingImpact() {
        MaintenanceWorkOrderDTO request = request();
        when(roomRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(room));
        when(reservationRoomRepository.hasActiveAssignment(eq(11L), anyList())).thenReturn(true);

        MaintenanceWorkOrderDTO result = service.create(request);

        assertEquals("OPEN", result.getStatus());
        assertTrue(result.isBookingImpact());
        verify(propertyAccessService).requireAccessibleOrNotFound(7L, "room");
        verify(historyRepository).save(argThat(item -> item.getHotel().getId().equals(7L)
                && item.getFromStatus() == null && "OPEN".equals(item.getToStatus())));
    }

    @Test
    void startLocksOrderAndRoomAndMovesRoomIntoMaintenance() {
        MaintenanceWorkOrder order = order("OPEN");
        when(repository.findByIdForUpdate(41L)).thenReturn(Optional.of(order));
        when(roomRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(room));

        MaintenanceWorkOrderDTO result = service.start(41L);

        assertEquals("IN_PROGRESS", result.getStatus());
        assertEquals("MAINTENANCE", room.getStatus());
        verify(roomRepository).save(room);
    }

    @Test
    void startRejectsBookingImpactWithoutChangingRoom() {
        when(repository.findByIdForUpdate(41L)).thenReturn(Optional.of(order("OPEN")));
        when(reservationRoomRepository.hasActiveAssignment(eq(11L), anyList())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.start(41L));
        verify(roomRepository, never()).save(any());
    }

    @Test
    void completeRestoresRoomAndRecordsResolution() {
        RoomStatePolicy.startMaintenance(room);
        when(repository.findByIdForUpdate(41L)).thenReturn(Optional.of(order("IN_PROGRESS")));
        when(roomRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(room));
        MaintenanceWorkOrderDTO request = new MaintenanceWorkOrderDTO(); request.setResolutionNote("Replaced valve");

        MaintenanceWorkOrderDTO result = service.complete(41L, request);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals("AVAILABLE", room.getStatus());
        assertEquals("Replaced valve", result.getResolutionNote());
    }

    @Test
    void rejectsCrossPropertyCreateBeforePersistence() {
        MaintenanceWorkOrderDTO request = request(); request.setPropertyId(99L);
        when(roomRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(room));

        assertThrows(com.hotel.exceptions.ResourceNotFoundException.class, () -> service.create(request));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAssigneeOutsideProperty() {
        MaintenanceWorkOrderDTO request = request(); request.setAssigneeUserId(55L);
        when(roomRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(room));

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(repository, never()).save(any());
    }

    @Test
    void cancelInProgressReleasesMaintenanceRoom() {
        RoomStatePolicy.startMaintenance(room);
        when(repository.findByIdForUpdate(41L)).thenReturn(Optional.of(order("IN_PROGRESS")));
        when(roomRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(room));
        MaintenanceWorkOrderDTO request = new MaintenanceWorkOrderDTO(); request.setReason("Vendor unavailable");

        assertEquals("CANCELLED", service.cancel(41L, request).getStatus());
        assertEquals("AVAILABLE", room.getStatus());
    }

    private MaintenanceWorkOrderDTO request() {
        MaintenanceWorkOrderDTO request = new MaintenanceWorkOrderDTO();
        request.setPropertyId(7L); request.setRoomId(11L); request.setReason("Air conditioner failure"); request.setPriority("HIGH");
        return request;
    }

    private MaintenanceWorkOrder order(String status) {
        MaintenanceWorkOrder order = new MaintenanceWorkOrder();
        order.setId(41L); order.setHotel(room.getHotel()); order.setRoom(room); order.setReason("Fault");
        order.setPriority("HIGH"); order.setStatus(status); return order;
    }
}

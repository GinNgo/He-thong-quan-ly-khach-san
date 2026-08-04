package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Reservation;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalExportServiceTest {
    @Mock PropertyAccessService propertyAccessService;
    @Mock ReservationRepository reservationRepository;
    @Mock RoomRepository roomRepository;
    @Mock HousekeepingTaskRepository housekeepingTaskRepository;

    private OperationalExportService service;

    @BeforeEach
    void setUp() {
        service = new OperationalExportService(propertyAccessService, reservationRepository, roomRepository, housekeepingTaskRepository);
    }

    @Test
    void reservationAndCustomerSchemasMinimizePiiAndApplyTenantFilters() {
        Reservation reservation = reservation();
        when(reservationRepository.findByHotelIdOrderByIdDesc(7L)).thenReturn(List.of(reservation));

        var reservations = service.export(7L, OperationalExportService.Dataset.RESERVATIONS, "CONFIRMED",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        String reservationCsv = text(reservations);
        assertTrue(reservationCsv.contains("RES-31"));
        assertTrue(reservationCsv.contains("CUS-9"));
        assertFalse(reservationCsv.contains("guest@example.com"));
        assertFalse(reservationCsv.contains("Late arrival"));

        var customers = service.export(7L, OperationalExportService.Dataset.CUSTOMERS, null, null, null);
        String customerCsv = text(customers);
        assertTrue(customerCsv.contains("gu***@example.com"));
        assertTrue(customerCsv.contains("***4567"));
        assertFalse(customerCsv.contains("Guest Name"));
        assertEquals(1, customers.rowCount());
        assertEquals(64, customers.checksum().length());
        verify(propertyAccessService, org.mockito.Mockito.times(2)).requireAssignedHotel(7L);
    }

    @Test
    void roomAndTaskSchemasExcludeNotesAndAssigneeIdentity() {
        Reservation reservation = reservation();
        Room room = reservation.getRoom();
        when(roomRepository.findByHotelId(7L)).thenReturn(List.of(room));
        HousekeepingTask task = new HousekeepingTask();
        task.setId(51L); task.setHotel(reservation.getHotel()); task.setRoom(room); task.setReservation(reservation);
        task.setStatus("PENDING"); task.setAssignedTo(reservation.getUser()); task.setNote("Private incident details");
        when(housekeepingTaskRepository.findByHotelIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(task));

        String roomCsv = text(service.export(7L, OperationalExportService.Dataset.ROOMS, "AVAILABLE", null, null));
        String taskCsv = text(service.export(7L, OperationalExportService.Dataset.HOUSEKEEPING, "PENDING", null, null));
        assertTrue(roomCsv.contains("ROOM-21"));
        assertTrue(taskCsv.contains("TASK-51"));
        assertTrue(taskCsv.contains("USR-9"));
        assertFalse(taskCsv.contains("Guest Name"));
        assertFalse(taskCsv.contains("Private incident details"));
    }

    @Test
    void deniesForeignPropertyBeforeReadingAnyDataset() {
        when(propertyAccessService.requireAssignedHotel(99L)).thenThrow(new RuntimeException("not found"));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.export(99L, OperationalExportService.Dataset.ROOMS, null, null, null));
        verifyNoInteractions(reservationRepository, roomRepository, housekeepingTaskRepository);
    }

    private Reservation reservation() {
        Hotel hotel = new Hotel(); hotel.setId(7L);
        User user = new User(); user.setId(9L); user.setFullName("Guest Name"); user.setEmail("guest@example.com"); user.setPhone("0901234567"); user.setStatus("ACTIVE");
        RoomType type = new RoomType(); type.setId(5L); type.setCode("DELUXE"); type.setHotel(hotel);
        Room room = new Room(); room.setId(21L); room.setHotel(hotel); room.setRoomType(type); room.setRoomNumber("D201"); room.setFloor(2); room.setStatus("AVAILABLE"); room.setHousekeepingStatus("CLEAN"); room.setMaintenanceStatus("NONE"); room.setMaxGuests(3);
        Reservation reservation = new Reservation(); reservation.setId(31L); reservation.setHotel(hotel); reservation.setUser(user); reservation.setRoom(room); reservation.setCheckInDate(LocalDate.of(2026, 7, 10)); reservation.setCheckOutDate(LocalDate.of(2026, 7, 12)); reservation.setGuests(2); reservation.setStatus("CONFIRMED"); reservation.setSpecialRequests("Late arrival");
        return reservation;
    }

    private String text(OperationalExportService.Artifact artifact) {
        return new String(artifact.content(), StandardCharsets.UTF_8);
    }
}

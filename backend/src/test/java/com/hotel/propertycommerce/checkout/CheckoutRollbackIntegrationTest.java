package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Injects failures at each checkout persistence boundary and verifies fail-closed ordering. */
@ExtendWith(MockitoExtension.class)
class CheckoutRollbackIntegrationTest {

    @Mock
    private ReservationRoomRepository reservationRoomRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private HousekeepingTaskRepository housekeepingTaskRepository;

    private CheckoutOperationsService service;
    private Reservation reservation;
    private ReservationRoom assignment;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new CheckoutOperationsService(
                reservationRoomRepository,
                roomRepository,
                housekeepingTaskRepository,
                Clock.fixed(Instant.parse("2026-08-01T02:00:00Z"), ZoneOffset.UTC));
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setStatus("CHECKED_IN");
        room = new Room();
        room.setId(12L);
        room.setHotel(hotel);
        room.setStatus("OCCUPIED");
        room.setHousekeepingStatus("CLEAN");
        ReservationDetail detail = new ReservationDetail();
        detail.setId(71L);
        detail.setReservation(reservation);
        assignment = new ReservationRoom();
        assignment.setId(81L);
        assignment.setReservationDetail(detail);
        assignment.setRoom(room);
        assignment.setStatus("ASSIGNED");
        when(reservationRoomRepository.findCheckoutAssignmentsByReservationIdForUpdate(42L))
                .thenReturn(List.of(assignment));
        when(roomRepository.findAllByIdForUpdate(List.of(12L))).thenReturn(List.of(room));
        when(housekeepingTaskRepository.findByHotelIdAndCheckoutEffectKey(
                3L, "CHECKOUT:42:ROOM:12")).thenReturn(Optional.empty());
        lenient().when(housekeepingTaskRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void housekeepingWriteFailureStopsBeforeAssignmentAndRoomWrites() {
        doThrow(new RuntimeException("housekeeping unavailable"))
                .when(housekeepingTaskRepository).saveAndFlush(any(HousekeepingTask.class));

        assertThatThrownBy(() -> service.apply(reservation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("housekeeping unavailable");

        verify(reservationRoomRepository, never()).saveAllAndFlush(any());
        verify(roomRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void assignmentWriteFailureStopsBeforeRoomWriteAndPropagatesForRollback() {
        doThrow(new RuntimeException("assignment unavailable"))
                .when(reservationRoomRepository).saveAllAndFlush(any());

        assertThatThrownBy(() -> service.apply(reservation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("assignment unavailable");

        verify(housekeepingTaskRepository).saveAndFlush(any(HousekeepingTask.class));
        verify(reservationRoomRepository).saveAllAndFlush(List.of(assignment));
        verify(roomRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void roomWriteFailureOccursAfterAssignmentWriteAndRequiresOuterTransactionRollback() {
        doThrow(new RuntimeException("room unavailable"))
                .when(roomRepository).saveAllAndFlush(any());

        assertThatThrownBy(() -> service.apply(reservation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("room unavailable");

        verify(housekeepingTaskRepository).saveAndFlush(any(HousekeepingTask.class));
        verify(reservationRoomRepository).saveAllAndFlush(List.of(assignment));
        verify(roomRepository).saveAllAndFlush(List.of(room));
    }
}

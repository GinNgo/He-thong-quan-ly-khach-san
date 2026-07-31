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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutOperationsServiceTest {

    @Mock private ReservationRoomRepository reservationRoomRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private HousekeepingTaskRepository housekeepingTaskRepository;

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
    }

    @Test
    void releasesAndDirtiesEachAssignedRoomAndCreatesOneKeyedTask() {
        arrangeAssignments();
        when(housekeepingTaskRepository.findByHotelIdAndCheckoutEffectKey(
                3L, "CHECKOUT:42:ROOM:12")).thenReturn(Optional.empty());
        when(housekeepingTaskRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutOperationsService.CheckoutOperationsResult result = service.apply(reservation);

        assertThat(assignment.getStatus()).isEqualTo("RELEASED");
        assertThat(assignment.getReleasedAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 2, 0));
        assertThat(room.getStatus()).isEqualTo("DIRTY");
        assertThat(room.getHousekeepingStatus()).isEqualTo("DIRTY");
        assertThat(result.roomIds()).containsExactly(12L);
        assertThat(result.releasedAssignmentCount()).isEqualTo(1);
        assertThat(result.createdHousekeepingTaskCount()).isEqualTo(1);
        verify(reservationRoomRepository).saveAllAndFlush(List.of(assignment));
        verify(roomRepository).saveAllAndFlush(List.of(room));

        ArgumentCaptor<HousekeepingTask> taskCaptor = ArgumentCaptor.forClass(HousekeepingTask.class);
        verify(housekeepingTaskRepository).saveAndFlush(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getCheckoutEffectKey()).isEqualTo("CHECKOUT:42:ROOM:12");
        assertThat(taskCaptor.getValue().getReservation()).isSameAs(reservation);
    }

    @Test
    void completedRetryDoesNotRedirtyCleanedRoomOrCreateAnotherTask() {
        assignment.setStatus("RELEASED");
        assignment.setReleasedAt(LocalDateTime.of(2026, 8, 1, 2, 0));
        room.setStatus("AVAILABLE");
        room.setHousekeepingStatus("CLEAN");
        arrangeAssignments();

        CheckoutOperationsService.CheckoutOperationsResult result = service.apply(reservation);

        assertThat(room.getStatus()).isEqualTo("AVAILABLE");
        assertThat(room.getHousekeepingStatus()).isEqualTo("CLEAN");
        assertThat(result.releasedAssignmentCount()).isZero();
        assertThat(result.createdHousekeepingTaskCount()).isZero();
        verify(reservationRoomRepository, never()).saveAllAndFlush(any());
        verify(roomRepository, never()).saveAllAndFlush(any());
        verify(housekeepingTaskRepository, never()).saveAndFlush(any());
    }

    @Test
    void existingCheckoutEffectPreventsDuplicateTaskWhileCompletingRoomTransition() {
        arrangeAssignments();
        HousekeepingTask existing = new HousekeepingTask();
        existing.setCheckoutEffectKey("CHECKOUT:42:ROOM:12");
        when(housekeepingTaskRepository.findByHotelIdAndCheckoutEffectKey(
                3L, "CHECKOUT:42:ROOM:12")).thenReturn(Optional.of(existing));

        CheckoutOperationsService.CheckoutOperationsResult result = service.apply(reservation);

        assertThat(result.releasedAssignmentCount()).isEqualTo(1);
        assertThat(result.createdHousekeepingTaskCount()).isZero();
        assertThat(room.getStatus()).isEqualTo("DIRTY");
        verify(housekeepingTaskRepository, never()).saveAndFlush(any());
    }

    @Test
    void crossPropertyRoomFailsClosedBeforeAnyOperationalSave() {
        Hotel otherHotel = new Hotel();
        otherHotel.setId(99L);
        room.setHotel(otherHotel);
        arrangeAssignments();

        assertThatThrownBy(() -> service.apply(reservation))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.CONCURRENT_MODIFICATION));

        assertThat(assignment.getStatus()).isEqualTo("ASSIGNED");
        verify(reservationRoomRepository, never()).saveAllAndFlush(any());
        verify(roomRepository, never()).saveAllAndFlush(any());
        verify(housekeepingTaskRepository, never()).saveAndFlush(any());
    }

    @Test
    void releasedAssignmentWithoutTimestampFailsClosed() {
        assignment.setStatus("RELEASED");
        arrangeAssignments();

        assertThatThrownBy(() -> service.apply(reservation))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.CONCURRENT_MODIFICATION));

        verify(reservationRoomRepository, never()).saveAllAndFlush(any());
        verify(roomRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void requiresAnExistingAggregateTransaction() throws Exception {
        Method method = CheckoutOperationsService.class.getMethod("apply", Reservation.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    private void arrangeAssignments() {
        when(reservationRoomRepository.findCheckoutAssignmentsByReservationIdForUpdate(42L))
                .thenReturn(List.of(assignment));
        when(roomRepository.findAllByIdForUpdate(List.of(12L))).thenReturn(List.of(room));
    }
}

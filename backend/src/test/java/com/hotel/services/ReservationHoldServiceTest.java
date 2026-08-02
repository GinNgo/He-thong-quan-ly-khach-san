package com.hotel.services;

import com.hotel.domain.lifecycle.ReservationHoldStatus;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationHold;
import com.hotel.entities.RoomType;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationHoldServiceTest {

    @Mock
    private ReservationHoldRepository holdRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationDetailRepository reservationDetailRepository;
    @Mock
    private RoomTypeRepository roomTypeRepository;

    private ReservationHoldService service;
    private Hotel hotel;
    private RoomType roomType;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        service = new ReservationHoldService(
                holdRepository,
                reservationRepository,
                reservationDetailRepository,
                roomTypeRepository,
                15);

        hotel = new Hotel();
        hotel.setId(10L);

        roomType = new RoomType();
        roomType.setId(20L);
        roomType.setHotel(hotel);

        reservation = new Reservation();
        reservation.setId(30L);
        reservation.setHotel(hotel);
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT.name());
    }

    @Test
    void createHoldPersistsTenantScopeQuantityAndConfiguredTtl() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 10, 0);
        ReservationDetail detail = new ReservationDetail();
        detail.setRoomType(roomType);
        detail.setQuantity(2);

        when(holdRepository.findByHoldKeyForUpdate("RESERVATION-30")).thenReturn(Optional.empty());
        when(reservationRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(reservation));
        when(roomTypeRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(roomType));
        when(reservationDetailRepository.findByReservationId(30L)).thenReturn(List.of(detail));
        when(holdRepository.findActiveByReservationIdForUpdate(30L)).thenReturn(Optional.empty());
        when(holdRepository.save(any(ReservationHold.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationHold created = service.createHold(30L, 20L, 2, " RESERVATION-30 ", now);

        assertThat(created.getHotel()).isSameAs(hotel);
        assertThat(created.getReservation()).isSameAs(reservation);
        assertThat(created.getRoomType()).isSameAs(roomType);
        assertThat(created.getQuantity()).isEqualTo(2);
        assertThat(created.getHoldKey()).isEqualTo("RESERVATION-30");
        assertThat(created.getStatus()).isEqualTo(ReservationHoldStatus.ACTIVE.name());
        assertThat(created.getExpiresAt()).isEqualTo(now.plusMinutes(15));
    }

    @Test
    void createHoldReplayReturnsExistingRecordWithoutDuplicatingIt() {
        ReservationHold existing = activeHold(LocalDateTime.now().plusMinutes(10));
        when(holdRepository.findByHoldKeyForUpdate("RESERVATION-30"))
                .thenReturn(Optional.of(existing));

        ReservationHold replayed = service.createHold(30L, 20L, 1, "RESERVATION-30");

        assertThat(replayed).isSameAs(existing);
        verify(holdRepository, never()).save(any());
        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(roomTypeRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void repeatedExpiryProcessesPersistedHoldExactlyOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 11, 0);
        ReservationHold hold = activeHold(now.minusMinutes(1));

        when(holdRepository.findExpiredActiveIds(now))
                .thenReturn(List.of(40L), List.of());
        when(holdRepository.findReservationIdById(40L)).thenReturn(Optional.of(30L));
        when(reservationRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(reservation));
        when(holdRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(hold));

        assertThat(service.expireDueHolds(now)).isEqualTo(1);
        LocalDateTime firstReleasedAt = hold.getReleasedAt();
        assertThat(service.expireDueHolds(now)).isZero();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED.name());
        assertThat(hold.getStatus()).isEqualTo(ReservationHoldStatus.EXPIRED.name());
        assertThat(hold.getReleasedAt()).isEqualTo(firstReleasedAt);
        verify(reservationRepository).save(reservation);
        verify(holdRepository).save(hold);
    }

    @Test
    void repeatedReleaseOnlyCompletesTheActiveHoldOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        ReservationHold hold = activeHold(now.plusMinutes(10));
        when(holdRepository.findActiveByReservationIdForUpdate(30L))
                .thenReturn(Optional.of(hold), Optional.empty());

        assertThat(service.releaseActiveHold(30L, now)).isTrue();
        assertThat(service.releaseActiveHold(30L, now.plusSeconds(1))).isFalse();

        assertThat(hold.getStatus()).isEqualTo(ReservationHoldStatus.RELEASED.name());
        assertThat(hold.getReleasedAt()).isEqualTo(now);
        verify(holdRepository).save(hold);
    }

    private ReservationHold activeHold(LocalDateTime expiresAt) {
        ReservationHold hold = new ReservationHold();
        hold.setId(40L);
        hold.setReservation(reservation);
        hold.setRoomType(roomType);
        hold.setHotel(hotel);
        hold.setQuantity(1);
        hold.setHoldKey("RESERVATION-30");
        hold.setStatus(ReservationHoldStatus.ACTIVE.name());
        hold.setExpiresAt(expiresAt);
        return hold;
    }
}

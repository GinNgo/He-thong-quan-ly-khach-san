package com.hotel.propertycommerce.folio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationChargeLineRepository extends JpaRepository<ReservationChargeLine, Long> {

    List<ReservationChargeLine> findByReservationIdOrderByCreatedAtAscIdAsc(Long reservationId);

    List<ReservationChargeLine> findByHotelIdAndReservationIdOrderByCreatedAtAscIdAsc(
            Long hotelId,
            Long reservationId);

    Optional<ReservationChargeLine> findByIdAndHotelIdAndReservationId(
            Long id,
            Long hotelId,
            Long reservationId);

    boolean existsByReversesLineId(Long lineId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select line from ReservationChargeLine line "
            + "where line.id = :id and line.hotel.id = :hotelId and line.reservation.id = :reservationId")
    Optional<ReservationChargeLine> findByIdForUpdate(
            @Param("id") Long id,
            @Param("hotelId") Long hotelId,
            @Param("reservationId") Long reservationId);
}

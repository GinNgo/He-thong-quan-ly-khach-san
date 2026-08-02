package com.hotel.repositories;

import com.hotel.entities.ReservationHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationHoldRepository extends JpaRepository<ReservationHold, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from ReservationHold hold where hold.holdKey = :holdKey")
    Optional<ReservationHold> findByHoldKeyForUpdate(@Param("holdKey") String holdKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from ReservationHold hold where hold.id = :id")
    Optional<ReservationHold> findByIdForUpdate(@Param("id") Long id);

    @Query("select hold.reservation.id from ReservationHold hold where hold.id = :id")
    Optional<Long> findReservationIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select hold from ReservationHold hold
            where hold.reservation.id = :reservationId
              and hold.status = 'ACTIVE'
            """)
    Optional<ReservationHold> findActiveByReservationIdForUpdate(@Param("reservationId") Long reservationId);

    @Query("""
            select hold.id from ReservationHold hold
            where hold.status = 'ACTIVE'
              and hold.expiresAt <= :now
            order by hold.expiresAt asc, hold.id asc
            """)
    List<Long> findExpiredActiveIds(@Param("now") LocalDateTime now);
}

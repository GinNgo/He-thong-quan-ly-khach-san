package com.hotel.repositories;

import com.hotel.entities.ReservationRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRoomRepository extends JpaRepository<ReservationRoom, Long> {
    List<ReservationRoom> findByReservationDetailReservationId(Long reservationId);
    List<ReservationRoom> findByReservationDetailIdAndStatus(Long reservationDetailId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from ReservationRoom assignment
            join fetch assignment.room room
            where assignment.reservationDetail.id = :reservationDetailId
              and assignment.status = :status
            order by assignment.id
            """)
    List<ReservationRoom> findByReservationDetailIdAndStatusForUpdate(
            @Param("reservationDetailId") Long reservationDetailId,
            @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from ReservationRoom assignment
            join fetch assignment.room room
            join assignment.reservationDetail detail
            where detail.reservation.id = :reservationId
              and assignment.status = 'ASSIGNED'
            order by assignment.id
            """)
    List<ReservationRoom> findAssignedByReservationIdForUpdate(@Param("reservationId") Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from ReservationRoom assignment
            join fetch assignment.room room
            join assignment.reservationDetail detail
            where detail.reservation.id = :reservationId
              and assignment.status in ('ASSIGNED', 'RELEASED')
            order by assignment.id
            """)
    List<ReservationRoom> findCheckoutAssignmentsByReservationIdForUpdate(
            @Param("reservationId") Long reservationId);

    @Query("""
            select case when count(assignment) > 0 then true else false end
            from ReservationRoom assignment
            join assignment.reservationDetail detail
            join detail.reservation reservation
            where assignment.room.id = :roomId
              and assignment.status = 'ASSIGNED'
              and reservation.id <> :reservationId
              and reservation.status not in :excludedStatuses
              and coalesce(assignment.stayStartDate, reservation.checkInDate) < :checkOut
              and coalesce(assignment.stayEndDate, reservation.checkOutDate) > :checkIn
            """)
    boolean hasConflictingAssignment(
            @Param("roomId") Long roomId,
            @Param("reservationId") Long reservationId,
            @Param("excludedStatuses") List<String> excludedStatuses,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
}

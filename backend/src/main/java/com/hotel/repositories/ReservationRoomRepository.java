package com.hotel.repositories;

import com.hotel.entities.ReservationRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRoomRepository extends JpaRepository<ReservationRoom, Long> {
    List<ReservationRoom> findByReservationDetailReservationId(Long reservationId);
    List<ReservationRoom> findByReservationDetailIdAndStatus(Long reservationDetailId, String status);

    @Query("""
            select case when count(assignment) > 0 then true else false end
            from ReservationRoom assignment
            join assignment.reservationDetail detail
            join detail.reservation reservation
            where assignment.room.id = :roomId
              and assignment.status = 'ASSIGNED'
              and reservation.id <> :reservationId
              and reservation.status not in :excludedStatuses
              and reservation.checkInDate < :checkOut
              and reservation.checkOutDate > :checkIn
            """)
    boolean hasConflictingAssignment(
            @Param("roomId") Long roomId,
            @Param("reservationId") Long reservationId,
            @Param("excludedStatuses") List<String> excludedStatuses,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
}

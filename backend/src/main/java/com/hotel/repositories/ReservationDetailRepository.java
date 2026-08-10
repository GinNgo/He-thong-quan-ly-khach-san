package com.hotel.repositories;

import com.hotel.entities.ReservationDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationDetailRepository extends JpaRepository<ReservationDetail, Long> {
    List<ReservationDetail> findByReservationId(Long reservationId);

    @Query("""
            select coalesce(sum(detail.quantity), 0)
            from ReservationDetail detail
            join detail.reservation reservation
            where detail.roomType.id = :roomTypeId
              and reservation.status not in :excludedStatuses
              and reservation.checkInDate < :checkOut
              and reservation.checkOutDate > :checkIn
            """)
    long sumReservedQuantity(
            @Param("roomTypeId") Long roomTypeId,
            @Param("excludedStatuses") List<String> excludedStatuses,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
}

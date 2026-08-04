package com.hotel.repositories;

import com.hotel.entities.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(Long id);

    List<Reservation> findByUserId(Long userId);
    List<Reservation> findByUserIdOrderByIdDesc(Long userId);
    List<Reservation> findByUserUsername(String username);
    List<Reservation> findByHotelIdIn(java.util.Collection<Long> hotelIds);
    List<Reservation> findByHotelIdOrderByIdDesc(Long hotelId);
    List<Reservation> findByStatus(String status);
    long countByUserIdAndStatusIn(Long userId, java.util.Collection<String> statuses);

    @Query("""
            select count(reservation)
            from Reservation reservation
            join reservation.hotel hotel
            where reservation.createdAt >= :fromInclusive
              and reservation.createdAt < :toExclusive
              and hotel.approvalStatus = 'APPROVED'
              and hotel.operationStatus = 'ACTIVE'
              and hotel.isDemo = false
              and reservation.status not in ('CANCELLED','EXPIRED','REJECTED','NO_SHOW')
            """)
    long countSystemBookingsCreatedBetween(
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);

    @Query("""
            select count(distinct reservation.room.id)
            from Reservation reservation
            join reservation.hotel hotel
            where reservation.room is not null
              and reservation.checkInDate <= :stayDate
              and reservation.checkOutDate > :stayDate
              and reservation.status not in ('CANCELLED','EXPIRED','REJECTED','NO_SHOW')
              and hotel.approvalStatus = 'APPROVED'
              and hotel.operationStatus = 'ACTIVE'
              and hotel.isDemo = false
              and not exists (
                  select assignment.id
                  from ReservationRoom assignment
                  join assignment.reservationDetail detail
                  where detail.reservation.id = reservation.id
                    and assignment.status = 'ASSIGNED'
              )
            """)
    long countSystemLegacyOccupiedRoomsOn(@Param("stayDate") LocalDate stayDate);

    Optional<Reservation> findByBookingIdempotencyScopeAndBookingIdempotencyKey(
            String bookingIdempotencyScope, String bookingIdempotencyKey);
}

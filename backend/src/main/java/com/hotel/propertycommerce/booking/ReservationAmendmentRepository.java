package com.hotel.propertycommerce.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationAmendmentRepository extends JpaRepository<ReservationAmendment, Long> {

    Optional<ReservationAmendment> findByPublicId(String publicId);

    Optional<ReservationAmendment> findByHotelIdAndIdempotencyKey(Long hotelId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select amendment from ReservationAmendment amendment where amendment.publicId = :publicId")
    Optional<ReservationAmendment> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select amendment from ReservationAmendment amendment
            where amendment.reservation.id = :reservationId
              and amendment.status in :statuses
            order by amendment.id desc
            """)
    List<ReservationAmendment> findActiveByReservationIdForUpdate(
            @Param("reservationId") Long reservationId,
            @Param("statuses") Collection<ReservationAmendment.Status> statuses);

    @Query("""
            select coalesce(sum(amendment.holdQuantity), 0)
            from ReservationAmendment amendment
            where amendment.proposedRoomType.id = :roomTypeId
              and amendment.status in :statuses
              and amendment.expiresAt > :now
              and amendment.proposedCheckIn < :checkOut
              and amendment.proposedCheckOut > :checkIn
              and (:excludedQuoteId is null or amendment.id <> :excludedQuoteId)
            """)
    long sumActiveHoldQuantity(
            @Param("roomTypeId") Long roomTypeId,
            @Param("statuses") Collection<ReservationAmendment.Status> statuses,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("excludedQuoteId") Long excludedQuoteId,
            @Param("now") LocalDateTime now);
}

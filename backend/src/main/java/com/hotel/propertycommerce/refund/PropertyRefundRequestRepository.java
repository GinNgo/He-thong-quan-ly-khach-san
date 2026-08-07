package com.hotel.propertycommerce.refund;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyRefundRequestRepository extends JpaRepository<PropertyRefundRequest, Long> {

    Optional<PropertyRefundRequest> findByPublicId(String publicId);

    Optional<PropertyRefundRequest> findByHotelIdAndIdempotencyKey(Long hotelId, String idempotencyKey);

    List<PropertyRefundRequest> findByOriginalTransactionIdOrderByRequestedAtAsc(Long transactionId);

    @Query("select r from PropertyRefundRequest r where r.hotel.id = :hotelId order by r.requestedAt desc")
    List<PropertyRefundRequest> findByHotelIdOrderByRequestedAtDesc(@Param("hotelId") Long hotelId);

    @Query("select r from PropertyRefundRequest r where r.originalTransaction.reservation.id = :reservationId order by r.requestedAt asc")
    List<PropertyRefundRequest> findByReservationIdOrderByRequestedAtAsc(@Param("reservationId") Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PropertyRefundRequest r where r.id = :id")
    Optional<PropertyRefundRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PropertyRefundRequest r where r.publicId = :publicId")
    Optional<PropertyRefundRequest> findByPublicIdForUpdate(@Param("publicId") String publicId);
}

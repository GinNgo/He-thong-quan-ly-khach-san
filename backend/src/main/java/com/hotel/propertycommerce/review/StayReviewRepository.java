package com.hotel.propertycommerce.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StayReviewRepository extends JpaRepository<StayReview, Long> {
    Optional<StayReview> findByReservationId(Long reservationId);
    List<StayReview> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<StayReview> findByHotelIdOrderByCreatedAtDesc(Long hotelId);
    List<StayReview> findByHotelIdAndStatusOrderByCreatedAtDesc(Long hotelId, StayReview.Status status);
    long countByCustomerIdAndCreatedAtAfter(Long customerId, LocalDateTime after);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StayReview r where r.id = :id")
    Optional<StayReview> findByIdForUpdate(Long id);

    @Query("select avg(r.rating), count(r) from StayReview r where r.hotel.id = :hotelId and r.status = 'PUBLISHED'")
    Object[] publishedAggregate(Long hotelId);
}

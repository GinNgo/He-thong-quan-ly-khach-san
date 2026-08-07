package com.hotel.repositories;

import com.hotel.entities.RefundRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    Optional<RefundRequest> findByOriginalPaymentId(Long originalPaymentId);

    List<RefundRequest> findByReservationIdOrderByIdAsc(Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from RefundRequest request where request.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") Long id);
}

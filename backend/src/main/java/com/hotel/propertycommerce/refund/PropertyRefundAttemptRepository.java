package com.hotel.propertycommerce.refund;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyRefundAttemptRepository extends JpaRepository<PropertyRefundAttempt, Long> {

    List<PropertyRefundAttempt> findByRefundRequestIdOrderByAttemptNumberAsc(Long refundRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PropertyRefundAttempt a where a.refundRequest.id = :requestId and a.attemptNumber = :attemptNumber")
    Optional<PropertyRefundAttempt> findForUpdate(
            @Param("requestId") Long requestId,
            @Param("attemptNumber") Integer attemptNumber);
}

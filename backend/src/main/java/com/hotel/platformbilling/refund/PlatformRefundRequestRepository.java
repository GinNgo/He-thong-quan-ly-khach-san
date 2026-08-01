package com.hotel.platformbilling.refund;

import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformRefundRequestRepository extends JpaRepository<PlatformRefundRequest, Long> {

    Optional<PlatformRefundRequest> findByPublicId(String publicId);

    Optional<PlatformRefundRequest> findByRequestedByIdAndIdempotencyKey(Long requestedById, String idempotencyKey);

    List<PlatformRefundRequest> findByOriginalTransactionOrderByRequestedAtAsc(PlatformFinancialTransaction transaction);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlatformRefundRequest r where r.id = :id")
    Optional<PlatformRefundRequest> findByIdForUpdate(@Param("id") Long id);
}

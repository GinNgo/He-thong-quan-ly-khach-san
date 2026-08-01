package com.hotel.platformbilling.refund;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformRefundAttemptRepository extends JpaRepository<PlatformRefundAttempt, Long> {

    List<PlatformRefundAttempt> findByRefundRequestIdOrderByAttemptNumberAsc(Long refundRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlatformRefundAttempt a where a.refundRequest.id = :requestId and a.attemptNumber = :attemptNumber")
    Optional<PlatformRefundAttempt> findForUpdate(
            @Param("requestId") Long requestId,
            @Param("attemptNumber") Integer attemptNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlatformRefundAttempt a where upper(a.provider) = upper(:provider) and a.providerReference = :reference")
    Optional<PlatformRefundAttempt> findByProviderAndReferenceForUpdate(
            @Param("provider") String provider,
            @Param("reference") String reference);
}

package com.hotel.platformbilling.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformFinancialTransactionRepository
        extends JpaRepository<PlatformFinancialTransaction, Long> {

    Optional<PlatformFinancialTransaction> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PlatformFinancialTransaction t where t.publicId = :publicId")
    Optional<PlatformFinancialTransaction> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<PlatformFinancialTransaction> findByIdempotencyIdentity(String idempotencyIdentity);

    List<PlatformFinancialTransaction> findByOrderIdOrderByOccurredAtAsc(Long orderId);

    List<PlatformFinancialTransaction> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);

    List<PlatformFinancialTransaction> findByOriginalTransactionIdOrderByOccurredAtAsc(Long originalTransactionId);
}

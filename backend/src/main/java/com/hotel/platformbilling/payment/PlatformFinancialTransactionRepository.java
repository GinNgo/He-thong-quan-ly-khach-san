package com.hotel.platformbilling.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformFinancialTransactionRepository
        extends JpaRepository<PlatformFinancialTransaction, Long> {

    Optional<PlatformFinancialTransaction> findByPublicId(String publicId);

    Optional<PlatformFinancialTransaction> findByIdempotencyIdentity(String idempotencyIdentity);

    List<PlatformFinancialTransaction> findByOrderIdOrderByOccurredAtAsc(Long orderId);

    List<PlatformFinancialTransaction> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);
}

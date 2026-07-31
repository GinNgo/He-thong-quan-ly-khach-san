package com.hotel.paymentprovider.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialIdempotencyRepository extends JpaRepository<FinancialIdempotencyRecord, Long> {

    Optional<FinancialIdempotencyRecord> findByContextAndOperationAndScopeKeyAndIdempotencyKey(
            String context, String operation, String scopeKey, String idempotencyKey);
}

package com.hotel.propertycommerce.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PropertyFinancialTransactionRepository
        extends JpaRepository<PropertyFinancialTransaction, Long> {

    Optional<PropertyFinancialTransaction> findByPublicId(String publicId);

    Optional<PropertyFinancialTransaction> findByIdempotencyIdentity(String idempotencyIdentity);

    List<PropertyFinancialTransaction> findByReservationIdOrderByOccurredAtAsc(Long reservationId);

    List<PropertyFinancialTransaction> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);
}

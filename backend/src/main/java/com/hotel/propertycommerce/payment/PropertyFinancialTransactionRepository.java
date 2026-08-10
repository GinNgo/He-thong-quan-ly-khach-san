package com.hotel.propertycommerce.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyFinancialTransactionRepository
        extends JpaRepository<PropertyFinancialTransaction, Long> {

    Optional<PropertyFinancialTransaction> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PropertyFinancialTransaction t where t.publicId = :publicId")
    Optional<PropertyFinancialTransaction> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<PropertyFinancialTransaction> findByIdempotencyIdentity(String idempotencyIdentity);

    List<PropertyFinancialTransaction> findByReservationIdOrderByOccurredAtAsc(Long reservationId);

    List<PropertyFinancialTransaction> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);

    List<PropertyFinancialTransaction> findByOriginalTransactionIdOrderByOccurredAtAsc(Long originalTransactionId);
}

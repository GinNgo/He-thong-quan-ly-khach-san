package com.hotel.propertycommerce.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface PropertyFinancialTransactionRepository
        extends JpaRepository<PropertyFinancialTransaction, Long> {

    Optional<PropertyFinancialTransaction> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PropertyFinancialTransaction t where t.publicId = :publicId")
    Optional<PropertyFinancialTransaction> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<PropertyFinancialTransaction> findByIdempotencyIdentity(String idempotencyIdentity);

    List<PropertyFinancialTransaction> findByReservationIdOrderByOccurredAtAsc(Long reservationId);

    List<PropertyFinancialTransaction> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select transaction
            from PropertyFinancialTransaction transaction
            where transaction.reservation.id = :reservationId
              and transaction.direction = :direction
              and transaction.transactionType in :types
              and transaction.legacyReconciliationRequired = false
            order by transaction.occurredAt, transaction.id
            """)
    List<PropertyFinancialTransaction> findBookingDebitsByReservationIdForUpdate(
            @Param("reservationId") Long reservationId,
            @Param("direction") PropertyFinancialTransaction.Direction direction,
            @Param("types") Collection<PropertyFinancialTransaction.TransactionType> types);

    List<PropertyFinancialTransaction> findByOriginalTransactionIdOrderByOccurredAtAsc(Long originalTransactionId);
}

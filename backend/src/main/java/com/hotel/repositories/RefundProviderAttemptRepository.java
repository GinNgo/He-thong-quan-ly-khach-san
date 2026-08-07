package com.hotel.repositories;

import com.hotel.entities.RefundProviderAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface RefundProviderAttemptRepository extends JpaRepository<RefundProviderAttempt, Long> {

    List<RefundProviderAttempt> findByRefundRequestIdOrderByAttemptNumberAsc(Long refundRequestId);

    @EntityGraph(attributePaths = {
            "refundRequest",
            "refundRequest.originalPayment",
            "refundRequest.reservation",
            "refundRequest.hotel"
    })
    List<RefundProviderAttempt> findTop50ByStatusInOrderByRequestedAtAsc(Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from RefundProviderAttempt attempt where attempt.refundRequest.id = :refundRequestId and attempt.attemptNumber = :attemptNumber")
    Optional<RefundProviderAttempt> findForUpdate(
            @Param("refundRequestId") Long refundRequestId,
            @Param("attemptNumber") Integer attemptNumber);
}

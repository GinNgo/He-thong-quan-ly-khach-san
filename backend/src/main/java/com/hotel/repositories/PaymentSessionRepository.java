package com.hotel.repositories;

import com.hotel.entities.PaymentSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PaymentSessionRepository extends JpaRepository<PaymentSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PaymentSession session where session.owner.id = :ownerId and session.idempotencyKey = :idempotencyKey")
    Optional<PaymentSession> findByOwnerIdAndIdempotencyKeyForUpdate(
            @Param("ownerId") Long ownerId,
            @Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PaymentSession session where session.reservation.id = :reservationId and session.status in ('CREATED','PENDING')")
    Optional<PaymentSession> findActiveByReservationIdForUpdate(@Param("reservationId") Long reservationId);

    @Query("select session.reservation.id from PaymentSession session where session.publicId = :publicId")
    Optional<Long> findReservationIdByPublicId(@Param("publicId") String publicId);

    @Query("select session.reservation.id from PaymentSession session where session.providerReference = :providerReference")
    Optional<Long> findReservationIdByProviderReference(@Param("providerReference") String providerReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PaymentSession session where session.publicId = :publicId")
    Optional<PaymentSession> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Query("select session from PaymentSession session where session.publicId = :publicId")
    Optional<PaymentSession> findByPublicId(@Param("publicId") String publicId);

    List<PaymentSession> findByReservationIdOrderByIdDesc(Long reservationId);

    List<PaymentSession> findTop50ByStatusInAndProviderInAndCreatedAtBeforeOrderByCreatedAtAsc(
            Collection<String> statuses,
            Collection<String> providers,
            LocalDateTime createdBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PaymentSession session where session.providerReference = :providerReference")
    Optional<PaymentSession> findByProviderReferenceForUpdate(@Param("providerReference") String providerReference);
}

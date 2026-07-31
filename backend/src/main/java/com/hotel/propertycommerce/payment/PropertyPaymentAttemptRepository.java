package com.hotel.propertycommerce.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyPaymentAttemptRepository extends JpaRepository<PropertyPaymentAttempt, Long> {

    Optional<PropertyPaymentAttempt> findByPublicId(String publicId);

    Optional<PropertyPaymentAttempt> findByHotelIdAndIdempotencyKey(Long hotelId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PropertyPaymentAttempt attempt "
            + "where attempt.hotel.id = :hotelId and attempt.idempotencyKey = :idempotencyKey")
    Optional<PropertyPaymentAttempt> findByHotelIdAndIdempotencyKeyForUpdate(
            @Param("hotelId") Long hotelId,
            @Param("idempotencyKey") String idempotencyKey);

    Optional<PropertyPaymentAttempt> findByProviderAndEnvironmentAndProviderEventId(
            String provider,
            com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment environment,
            String providerEventId);

    List<PropertyPaymentAttempt> findByReservationIdOrderByCreatedAtAsc(Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PropertyPaymentAttempt attempt where attempt.publicId = :publicId")
    Optional<PropertyPaymentAttempt> findByPublicIdForUpdate(@Param("publicId") String publicId);
}

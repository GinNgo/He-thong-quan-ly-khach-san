package com.hotel.platformbilling.payment;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformPaymentAttemptRepository extends JpaRepository<PlatformPaymentAttempt, Long> {

    Optional<PlatformPaymentAttempt> findByPublicId(String publicId);

    Optional<PlatformPaymentAttempt> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);

    Optional<PlatformPaymentAttempt> findByProviderAndEnvironmentAndProviderEventId(
            String provider,
            PaymentEnvironment environment,
            String providerEventId);

    List<PlatformPaymentAttempt> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PlatformPaymentAttempt attempt where attempt.publicId = :publicId")
    Optional<PlatformPaymentAttempt> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PlatformPaymentAttempt attempt "
            + "where attempt.provider = :provider and attempt.environment = :environment "
            + "and attempt.providerEventId = :providerEventId")
    Optional<PlatformPaymentAttempt> findByProviderEventForUpdate(
            @Param("provider") String provider,
            @Param("environment") PaymentEnvironment environment,
            @Param("providerEventId") String providerEventId);
}

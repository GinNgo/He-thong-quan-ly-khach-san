package com.hotel.platformbilling.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LegacySubscriptionEntitlementProjectionRepository
        extends JpaRepository<LegacySubscriptionEntitlementProjection, Long> {

    Optional<LegacySubscriptionEntitlementProjection> findByTargetHotelId(Long targetHotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select projection from LegacySubscriptionEntitlementProjection projection "
            + "where projection.targetHotel.id = :targetHotelId")
    Optional<LegacySubscriptionEntitlementProjection> findByTargetHotelIdForUpdate(
            @Param("targetHotelId") Long targetHotelId);
}

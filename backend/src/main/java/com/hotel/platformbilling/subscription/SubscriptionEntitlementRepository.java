package com.hotel.platformbilling.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubscriptionEntitlementRepository extends JpaRepository<SubscriptionEntitlement, Long> {

    Optional<SubscriptionEntitlement> findByTargetHotelId(Long targetHotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entitlement from SubscriptionEntitlement entitlement "
            + "where entitlement.targetHotel.id = :targetHotelId")
    Optional<SubscriptionEntitlement> findByTargetHotelIdForUpdate(@Param("targetHotelId") Long targetHotelId);
}

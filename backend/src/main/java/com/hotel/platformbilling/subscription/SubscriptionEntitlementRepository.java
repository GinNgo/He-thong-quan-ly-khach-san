package com.hotel.platformbilling.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface SubscriptionEntitlementRepository extends JpaRepository<SubscriptionEntitlement, Long> {

    Optional<SubscriptionEntitlement> findByTargetHotelId(Long targetHotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entitlement from SubscriptionEntitlement entitlement "
            + "where entitlement.targetHotel.id = :targetHotelId")
    Optional<SubscriptionEntitlement> findByTargetHotelIdForUpdate(@Param("targetHotelId") Long targetHotelId);

    @Query("select entitlement.targetHotel.id from SubscriptionEntitlement entitlement "
            + "where entitlement.status = 'ACTIVE' and entitlement.lifetime = false "
            + "and entitlement.effectiveUntil <= :cutoff order by entitlement.targetHotel.id")
    List<Long> findDueHotelIds(@Param("cutoff") java.time.LocalDateTime cutoff, Pageable pageable);
}

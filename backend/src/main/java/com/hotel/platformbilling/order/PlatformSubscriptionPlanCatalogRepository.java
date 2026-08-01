package com.hotel.platformbilling.order;

import com.hotel.entities.SubscriptionPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformSubscriptionPlanCatalogRepository extends JpaRepository<SubscriptionPlan, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select distinct plan from SubscriptionPlan plan "
            + "left join fetch plan.features where plan.id = :planId")
    Optional<SubscriptionPlan> findByIdForSnapshot(@Param("planId") Long planId);
}

package com.hotel.repositories;

import com.hotel.entities.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByCode(String code);
    Optional<SubscriptionPlan> findByCreationKeyHash(String creationKeyHash);
    List<SubscriptionPlan> findByStatusOrderByPriceAsc(String status);
    List<SubscriptionPlan> findByFamilyCodeOrderByVersionNumberDesc(String familyCode);
    List<SubscriptionPlan> findAllByOrderByFamilyCodeAscVersionNumberDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from SubscriptionPlan plan left join fetch plan.features where plan.id = :id")
    Optional<SubscriptionPlan> findVersionForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from SubscriptionPlan plan where plan.familyCode = :familyCode order by plan.versionNumber desc, plan.id desc")
    List<SubscriptionPlan> findFamilyForUpdate(@Param("familyCode") String familyCode);

    @Query("select plan.familyCode from SubscriptionPlan plan where plan.id = :id")
    Optional<String> findFamilyCodeById(@Param("id") Long id);
}

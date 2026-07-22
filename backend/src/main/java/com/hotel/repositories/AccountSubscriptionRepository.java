package com.hotel.repositories;

import com.hotel.entities.AccountSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface AccountSubscriptionRepository extends JpaRepository<AccountSubscription, Long> {
    List<AccountSubscription> findByUserIdAndStatus(Long userId, String status);

    @Query("""
            select subscription
            from AccountSubscription subscription
            where subscription.user.id = :userId
              and subscription.status = 'ACTIVE'
              and subscription.startAt <= CURRENT_TIMESTAMP
              and (
                    subscription.isLifetime = true
                    or subscription.endAt is null
                    or subscription.endAt >= CURRENT_TIMESTAMP
              )
            order by subscription.startAt desc
            """)
    List<AccountSubscription> findEffectiveSubscriptionsByUserId(@Param("userId") Long userId);

    List<AccountSubscription> findByUserId(Long userId);
    Optional<AccountSubscription> findFirstByUserIdOrderByStartAtDesc(Long userId);
    Optional<AccountSubscription> findFirstByUserIdAndPlanCodeOrderByStartAtDesc(Long userId, String planCode);
}

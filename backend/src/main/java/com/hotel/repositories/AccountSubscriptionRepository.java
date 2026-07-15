package com.hotel.repositories;

import com.hotel.entities.AccountSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface AccountSubscriptionRepository extends JpaRepository<AccountSubscription, Long> {
    List<AccountSubscription> findByUserIdAndStatus(Long userId, String status);
    List<AccountSubscription> findByUserId(Long userId);
    Optional<AccountSubscription> findFirstByUserIdOrderByStartAtDesc(Long userId);
    Optional<AccountSubscription> findFirstByUserIdAndPlanCodeOrderByStartAtDesc(Long userId, String planCode);
}

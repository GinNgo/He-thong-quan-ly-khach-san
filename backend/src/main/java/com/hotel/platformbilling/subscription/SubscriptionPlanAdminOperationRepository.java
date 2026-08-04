package com.hotel.platformbilling.subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SubscriptionPlanAdminOperationRepository extends JpaRepository<SubscriptionPlanAdminOperation,Long>{ Optional<SubscriptionPlanAdminOperation> findByKeyHash(String keyHash); }

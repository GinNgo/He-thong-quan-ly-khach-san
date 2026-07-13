package com.hotel.repositories;

import com.hotel.entities.SubscriptionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, Long> {
    Optional<SubscriptionOrder> findByOrderCode(String orderCode);
    List<SubscriptionOrder> findByUserId(Long userId);
}

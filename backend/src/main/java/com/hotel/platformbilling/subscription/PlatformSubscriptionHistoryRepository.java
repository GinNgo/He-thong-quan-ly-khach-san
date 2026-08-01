package com.hotel.platformbilling.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformSubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

    List<SubscriptionHistory> findByTargetHotelIdOrderByOccurredAtDesc(Long targetHotelId);

    List<SubscriptionHistory> findByOrderIdOrderByOccurredAtAsc(Long orderId);

    boolean existsByOrderIdAndActionType(Long orderId, SubscriptionHistory.ActionType actionType);
}

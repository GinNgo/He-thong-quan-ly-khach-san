package com.hotel.platformbilling.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformSubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, Long> {

    Optional<SubscriptionOrder> findByPublicId(String publicId);

    Optional<SubscriptionOrder> findByOrderCode(String orderCode);

    Optional<SubscriptionOrder> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    List<SubscriptionOrder> findByTargetHotelIdOrderByCreatedAtDesc(Long targetHotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from PlatformSubscriptionOrder orders where orders.publicId = :publicId")
    Optional<SubscriptionOrder> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from PlatformSubscriptionOrder orders "
            + "where orders.owner.id = :ownerId and orders.idempotencyKey = :idempotencyKey")
    Optional<SubscriptionOrder> findByOwnerIdAndIdempotencyKeyForUpdate(
            @Param("ownerId") Long ownerId,
            @Param("idempotencyKey") String idempotencyKey);
}

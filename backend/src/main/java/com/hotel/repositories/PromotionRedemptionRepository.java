package com.hotel.repositories;

import com.hotel.entities.PromotionRedemption;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, Long> {

    Optional<PromotionRedemption> findByIdempotencyKey(String idempotencyKey);

    List<PromotionRedemption> findByReservationIdOrderByIdAsc(Long reservationId);

    long countByCampaignIdAndStatusIn(Long campaignId, Collection<String> statuses);

    long countByCampaignIdAndCustomerIdAndStatusIn(Long campaignId, Long customerId, Collection<String> statuses);

    @Query("""
            select coalesce(sum(redemption.discountAmount), 0)
            from PromotionRedemption redemption
            where redemption.campaign.id = :campaignId
              and redemption.status in ('RESERVED', 'APPLIED')
            """)
    BigDecimal sumCommittedDiscount(@Param("campaignId") Long campaignId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select redemption from PromotionRedemption redemption where redemption.idempotencyKey = :idempotencyKey")
    Optional<PromotionRedemption> findByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);
}

package com.hotel.repositories;

import com.hotel.entities.PromotionCampaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, Long> {

    Optional<PromotionCampaign> findByCodeAndHotelId(String code, Long hotelId);

    Optional<PromotionCampaign> findByCodeAndHotelIsNull(String code);

    List<PromotionCampaign> findByHotelIdAndStatusOrderByPriorityDescIdDesc(Long hotelId, String status);

    List<PromotionCampaign> findByHotelIsNullAndStatusOrderByPriorityDescIdDesc(String status);

    @Query("""
            select campaign from PromotionCampaign campaign
            where campaign.status = 'ACTIVE'
              and campaign.startsAt <= :at
              and campaign.endsAt > :at
            order by campaign.priority desc, campaign.id asc
            """)
    List<PromotionCampaign> findPublicActive(@Param("at") Instant at);

    long countByHotelIdAndStatusIn(Long hotelId, Collection<String> statuses);

    @Query("""
            select campaign from PromotionCampaign campaign
            where campaign.status = 'ACTIVE'
              and campaign.startsAt <= :at
              and campaign.endsAt > :at
              and (campaign.hotel.id is null or campaign.hotel.id in :hotelIds)
              and (:applicationType is null or campaign.applicationType = :applicationType)
            order by campaign.priority desc, campaign.id asc
            """)
    List<PromotionCampaign> findEligibleCampaigns(
            @Param("hotelIds") Collection<Long> hotelIds,
            @Param("applicationType") String applicationType,
            @Param("at") Instant at);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from PromotionCampaign campaign where campaign.id = :id")
    Optional<PromotionCampaign> findByIdForUpdate(@Param("id") Long id);
}

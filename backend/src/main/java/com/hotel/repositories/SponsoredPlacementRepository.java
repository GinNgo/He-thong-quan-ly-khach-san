package com.hotel.repositories;

import com.hotel.entities.SponsoredPlacement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SponsoredPlacementRepository extends JpaRepository<SponsoredPlacement, Long> {

    List<SponsoredPlacement> findByHotelIdOrderByStartsAtDescIdDesc(Long hotelId);

    List<SponsoredPlacement> findByHotelIsNullOrderByStartsAtDescIdDesc();

    long countByHotelIdAndStatusIn(Long hotelId, Collection<String> statuses);

    @Query("""
            select placement from SponsoredPlacement placement
            left join fetch placement.hotel ownerHotel
            left join fetch placement.targetHotel targetHotel
            where placement.placementSurface = :surface
              and placement.status = 'ACTIVE'
              and placement.approvedAt is not null
              and placement.startsAt <= :at
              and placement.endsAt > :at
              and (placement.impressionLimit is null or placement.impressionCount < placement.impressionLimit)
              and (placement.clickLimit is null or placement.clickCount < placement.clickLimit)
              and (placement.budget is null or placement.spentAmount < placement.budget)
            order by placement.sortPriority desc, placement.id desc
            """)
    List<SponsoredPlacement> findEligiblePublicPlacements(
            @Param("surface") String surface,
            @Param("at") Instant at,
            Pageable pageable);

    @Query("""
            select placement from SponsoredPlacement placement
            left join fetch placement.hotel ownerHotel
            left join fetch placement.targetHotel targetHotel
            where placement.placementSurface = :surface
              and placement.targetHotel.id in :hotelIds
              and placement.status = 'ACTIVE'
              and placement.approvedAt is not null
              and placement.startsAt <= :at
              and placement.endsAt > :at
              and (placement.impressionLimit is null or placement.impressionCount < placement.impressionLimit)
              and (placement.clickLimit is null or placement.clickCount < placement.clickLimit)
              and (placement.budget is null or placement.spentAmount < placement.budget)
            order by placement.sortPriority desc, placement.id desc
            """)
    List<SponsoredPlacement> findEligiblePublicSearchPlacements(
            @Param("surface") String surface,
            @Param("hotelIds") Collection<Long> hotelIds,
            @Param("at") Instant at,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select placement from SponsoredPlacement placement where placement.id = :id")
    Optional<SponsoredPlacement> findByIdForUpdate(@Param("id") Long id);
}

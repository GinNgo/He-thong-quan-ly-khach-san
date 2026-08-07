package com.hotel.repositories;

import com.hotel.entities.MembershipTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipTierRepository extends JpaRepository<MembershipTier, Long> {

    Optional<MembershipTier> findByCodeAndHotelId(String code, Long hotelId);

    Optional<MembershipTier> findByCodeAndHotelIsNull(String code);

    List<MembershipTier> findByHotelIdAndStatusOrderByTierRankAsc(Long hotelId, String status);

    List<MembershipTier> findByHotelIsNullAndStatusOrderByTierRankAsc(String status);
}


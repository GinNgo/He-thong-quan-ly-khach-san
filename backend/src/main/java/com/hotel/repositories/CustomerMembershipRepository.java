package com.hotel.repositories;

import com.hotel.entities.CustomerMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CustomerMembershipRepository extends JpaRepository<CustomerMembership, Long> {

    @Query("""
            select membership from CustomerMembership membership
            join fetch membership.tier tier
            where membership.customer.id = :customerId
              and membership.status = 'ACTIVE'
              and membership.startsAt <= :at
              and (membership.endsAt is null or membership.endsAt > :at)
              and (membership.hotel.id is null or membership.hotel.id = :hotelId)
              and tier.status = 'ACTIVE'
            order by tier.tierRank desc, membership.id desc
            """)
    List<CustomerMembership> findActiveMemberships(
            @Param("customerId") Long customerId,
            @Param("hotelId") Long hotelId,
            @Param("at") Instant at);

    List<CustomerMembership> findByCustomerIdOrderByStartsAtDesc(Long customerId);
}


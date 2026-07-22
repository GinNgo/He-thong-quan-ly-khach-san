package com.hotel.repositories;

import com.hotel.entities.UserProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserPropertyRepository extends JpaRepository<UserProperty, Long> {
    List<UserProperty> findByUserId(Long userId);
    List<UserProperty> findByUserIdAndRelationshipType(Long userId, String relationshipType);
    List<UserProperty> findByHotelId(Long hotelId);
    java.util.Optional<UserProperty> findByUserIdAndHotelIdAndRelationshipType(Long userId, Long hotelId, String relationshipType);
    long countByUserIdAndStatus(Long userId, String status);

    @Query("""
            select count(distinct up.user.id)
            from UserProperty up
            where up.hotel.id in :hotelIds
              and up.status = 'ACTIVE'
              and up.relationshipType <> 'OWNER'
            """)
    long countActiveStaffByHotelIds(@Param("hotelIds") Collection<Long> hotelIds);
}

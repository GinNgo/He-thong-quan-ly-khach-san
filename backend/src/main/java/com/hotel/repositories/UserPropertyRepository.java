package com.hotel.repositories;

import com.hotel.entities.UserProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserPropertyRepository extends JpaRepository<UserProperty, Long> {
    List<UserProperty> findByUserId(Long userId);
    List<UserProperty> findByUserIdAndRelationshipType(Long userId, String relationshipType);
    List<UserProperty> findByUserIdAndRelationshipTypeOrderByStartDateDesc(Long userId, String relationshipType);
    List<UserProperty> findByHotelId(Long hotelId);
    java.util.Optional<UserProperty> findByUserIdAndHotelIdAndRelationshipType(Long userId, Long hotelId, String relationshipType);
    List<UserProperty> findByHotelIdAndRelationshipTypeAndStatus(Long hotelId, String relationshipType, String status);
    long countByHotelIdAndRelationshipTypeAndStatus(Long hotelId, String relationshipType, String status);
    long countByUserIdAndRelationshipTypeAndStatus(Long userId, String relationshipType, String status);
    long countByUserIdAndStatus(Long userId, String status);

    @Query("""
            select up
            from UserProperty up
            join fetch up.hotel
            where up.user.id = :userId
              and up.relationshipType = 'OWNER'
            order by up.id desc
            """)
    List<UserProperty> findOwnerMappingsWithHotelByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select up
            from UserProperty up
            where up.user.id = :userId
              and up.relationshipType = 'STAFF'
            order by up.startDate desc, up.id desc
            """)
    List<UserProperty> findStaffAssignmentsForUpdate(@Param("userId") Long userId);

    @Query("""
            select distinct up.user
            from UserProperty up
            where up.hotel.id in :hotelIds
              and up.relationshipType <> 'OWNER'
            """)
    List<com.hotel.entities.User> findHistoricalStaffUsersByHotelIds(
            @Param("hotelIds") Collection<Long> hotelIds);

    @Query("""
            select count(up)
            from UserProperty up
            where up.user.id = :userId
              and up.status = 'ACTIVE'
              and up.relationshipType = 'OWNER'
            """)
    long countActiveOwnedPropertiesByUserId(@Param("userId") Long userId);

    @Query("""
            select count(distinct up.user.id)
            from UserProperty up
            where up.hotel.id in :hotelIds
              and up.status = 'ACTIVE'
              and up.relationshipType <> 'OWNER'
            """)
    long countActiveStaffByHotelIds(@Param("hotelIds") Collection<Long> hotelIds);

    @Query("""
            select count(distinct up.user.id)
            from UserProperty up
            where up.hotel.id = :hotelId
              and up.status = 'ACTIVE'
              and up.relationshipType = 'STAFF'
            """)
    long countActiveStaffByHotelId(@Param("hotelId") Long hotelId);
}

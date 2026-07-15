package com.hotel.repositories;

import com.hotel.entities.UserProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPropertyRepository extends JpaRepository<UserProperty, Long> {
    List<UserProperty> findByUserId(Long userId);
    List<UserProperty> findByHotelId(Long hotelId);
    java.util.Optional<UserProperty> findByUserIdAndHotelIdAndRelationshipType(Long userId, Long hotelId, String relationshipType);
    long countByUserIdAndStatus(Long userId, String status);
}

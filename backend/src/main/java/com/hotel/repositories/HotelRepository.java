package com.hotel.repositories;

import com.hotel.entities.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    List<Hotel> findByAddressLineContainingIgnoreCaseAndStatus(String addressLine, String status);
    List<Hotel> findByStatus(String status);
    @org.springframework.data.jpa.repository.Query("SELECT up.hotel FROM UserProperty up WHERE up.user.id = :ownerId AND up.relationshipType = 'OWNER'")
    List<Hotel> findByOwnerId(@org.springframework.data.repository.query.Param("ownerId") Long ownerId);
}

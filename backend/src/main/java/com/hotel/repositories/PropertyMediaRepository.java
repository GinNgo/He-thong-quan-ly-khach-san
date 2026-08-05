package com.hotel.repositories;

import com.hotel.entities.PropertyMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyMediaRepository extends JpaRepository<PropertyMedia, Long> {
    List<PropertyMedia> findByHotelIdAndStatusOrderByIdDesc(Long hotelId, String status);
    long countByHotelId(Long hotelId);
}

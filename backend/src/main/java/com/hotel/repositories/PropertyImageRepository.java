package com.hotel.repositories;

import com.hotel.entities.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyImageRepository extends JpaRepository<PropertyImage, Long> {
    List<PropertyImage> findByHotelId(Long hotelId);
    List<PropertyImage> findByHotelIdOrderBySortOrderAscIdAsc(Long hotelId);
    long countByHotelId(Long hotelId);
}

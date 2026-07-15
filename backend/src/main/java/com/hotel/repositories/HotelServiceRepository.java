package com.hotel.repositories;

import com.hotel.entities.HotelService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HotelServiceRepository extends JpaRepository<HotelService, Long> {
    Optional<HotelService> findByCode(String code);
    java.util.List<HotelService> findByHotelIdOrSystemServiceTrue(Long hotelId);
}

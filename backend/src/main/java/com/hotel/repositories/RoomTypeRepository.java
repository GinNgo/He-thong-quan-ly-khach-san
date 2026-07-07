package com.hotel.repositories;

import com.hotel.entities.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    Optional<RoomType> findByCode(String code);
    java.util.List<RoomType> findByHotelId(Long hotelId);
    Optional<RoomType> findByCodeAndHotelId(String code, Long hotelId);
}

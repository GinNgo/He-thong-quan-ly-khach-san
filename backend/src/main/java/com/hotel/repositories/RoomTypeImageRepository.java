package com.hotel.repositories;

import com.hotel.entities.RoomTypeImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomTypeImageRepository extends JpaRepository<RoomTypeImage, Long> {
    List<RoomTypeImage> findByRoomTypeIdOrderBySortOrderAsc(Long roomTypeId);
    long countByRoomTypeHotelId(Long hotelId);
    void deleteByRoomTypeId(Long roomTypeId);
}

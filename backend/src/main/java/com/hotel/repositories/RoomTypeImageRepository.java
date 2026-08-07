package com.hotel.repositories;

import com.hotel.entities.RoomTypeImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomTypeImageRepository extends JpaRepository<RoomTypeImage, Long> {
    List<RoomTypeImage> findByRoomTypeIdOrderBySortOrderAsc(Long roomTypeId);
    boolean existsByRoomTypeIdAndImageUrl(Long roomTypeId, String imageUrl);
    long countByRoomTypeHotelId(Long hotelId);
<<<<<<< HEAD
    long countByMediaId(Long mediaId);
=======
    long countByIsDemoTrue();
>>>>>>> codex/ui-functional-audit-polish
    void deleteByRoomTypeId(Long roomTypeId);
}

package com.hotel.repositories;

import com.hotel.entities.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByStatus(String status);
    List<Room> findByFloor(Integer floor);
    List<Room> findByRoomTypeId(Long roomTypeId);
}

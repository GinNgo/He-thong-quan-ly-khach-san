package com.hotel.repositories;

import com.hotel.entities.HousekeepingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HousekeepingTaskRepository extends JpaRepository<HousekeepingTask, Long> {
    List<HousekeepingTask> findByHotelIdAndStatusOrderByCreatedAtAsc(Long hotelId, String status);
    List<HousekeepingTask> findByRoomIdAndStatus(Long roomId, String status);
    long countByHotelIdAndStatus(Long hotelId, String status);
}

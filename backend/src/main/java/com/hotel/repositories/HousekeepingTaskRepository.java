package com.hotel.repositories;

import com.hotel.entities.HousekeepingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HousekeepingTaskRepository extends JpaRepository<HousekeepingTask, Long> {
    List<HousekeepingTask> findByHotelIdAndStatusOrderByCreatedAtAsc(Long hotelId, String status);
    List<HousekeepingTask> findByHotelIdOrderByCreatedAtDesc(Long hotelId);
    List<HousekeepingTask> findByRoomIdAndStatus(Long roomId, String status);
    Optional<HousekeepingTask> findByHotelIdAndCheckoutEffectKey(Long hotelId, String checkoutEffectKey);
    long countByHotelIdAndStatus(Long hotelId, String status);
}

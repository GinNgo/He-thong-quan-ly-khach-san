package com.hotel.repositories;

import com.hotel.entities.HousekeepingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface HousekeepingTaskRepository extends JpaRepository<HousekeepingTask, Long> {
    List<HousekeepingTask> findByHotelIdAndStatusOrderByCreatedAtAsc(Long hotelId, String status);
<<<<<<< HEAD
    List<HousekeepingTask> findByHotelIdOrderByCreatedAtDesc(Long hotelId);
=======
    List<HousekeepingTask> findByHotelIdAndStatusInOrderByCreatedAtAsc(Long hotelId, List<String> statuses);
>>>>>>> codex/ui-functional-audit-polish
    List<HousekeepingTask> findByRoomIdAndStatus(Long roomId, String status);
    Optional<HousekeepingTask> findByHotelIdAndCheckoutEffectKey(Long hotelId, String checkoutEffectKey);
    long countByHotelIdAndStatus(Long hotelId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from HousekeepingTask task join fetch task.room room where task.id = :id")
    Optional<HousekeepingTask> findByIdForUpdate(@Param("id") Long id);
}

package com.hotel.operations;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OperationalTaskRepository extends JpaRepository<OperationalTask, Long> {
    List<OperationalTask> findByHotelIdAndStatusOrderByCreatedAtAsc(Long hotelId, OperationalTask.Status status);
    List<OperationalTask> findByHotelIdOrderByCreatedAtAsc(Long hotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from OperationalTask task where task.id = :id")
    Optional<OperationalTask> findByIdForUpdate(@Param("id") Long id);
}


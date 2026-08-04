package com.hotel.repositories;

import com.hotel.entities.HotelServiceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelServiceHistoryRepository extends JpaRepository<HotelServiceHistory, Long> {
    List<HotelServiceHistory> findByServiceIdOrderByIdAsc(Long serviceId);
}

package com.hotel.repositories;

import com.hotel.entities.MaintenanceWorkOrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceWorkOrderHistoryRepository extends JpaRepository<MaintenanceWorkOrderHistory, Long> {
    List<MaintenanceWorkOrderHistory> findByWorkOrderIdOrderByIdAsc(Long workOrderId);
}

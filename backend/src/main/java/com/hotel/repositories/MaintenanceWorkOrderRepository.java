package com.hotel.repositories;

import com.hotel.entities.MaintenanceWorkOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaintenanceWorkOrderRepository extends JpaRepository<MaintenanceWorkOrder, Long> {
    List<MaintenanceWorkOrder> findByHotelIdInOrderByIdDesc(Collection<Long> hotelIds);
    List<MaintenanceWorkOrder> findByHotelIdOrderByIdDesc(Long hotelId);
    List<MaintenanceWorkOrder> findByHotelIdAndRoomIdOrderByIdDesc(Long hotelId, Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workOrder from MaintenanceWorkOrder workOrder where workOrder.id = :id")
    Optional<MaintenanceWorkOrder> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRoomIdAndStatusIn(Long roomId, Collection<String> statuses);
}

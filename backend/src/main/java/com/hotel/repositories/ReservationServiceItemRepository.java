package com.hotel.repositories;

import com.hotel.entities.ReservationServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationServiceItemRepository extends JpaRepository<ReservationServiceItem, Long> {
    List<ReservationServiceItem> findByReservationId(Long reservationId);
}

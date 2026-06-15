package com.hotel.repositories;

import com.hotel.entities.ReservationDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservationDetailRepository extends JpaRepository<ReservationDetail, Long> {
    List<ReservationDetail> findByReservationId(Long reservationId);
}

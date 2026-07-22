package com.hotel.repositories;

import com.hotel.entities.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(Long id);

    List<Reservation> findByUserId(Long userId);
    List<Reservation> findByHotelIdIn(java.util.Collection<Long> hotelIds);
    List<Reservation> findByStatus(String status);
    long countByUserIdAndStatusIn(Long userId, java.util.Collection<String> statuses);
}

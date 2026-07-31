package com.hotel.propertycommerce.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingFinancialSummaryRepository extends JpaRepository<BookingFinancialSummary, Long> {

    Optional<BookingFinancialSummary> findByReservationId(Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select summary from BookingFinancialSummary summary where summary.reservation.id = :reservationId")
    Optional<BookingFinancialSummary> findByReservationIdForUpdate(@Param("reservationId") Long reservationId);
}

package com.hotel.propertycommerce.booking.staff;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StaffBookingQuoteRepository extends JpaRepository<StaffBookingQuote, Long> {
    Optional<StaffBookingQuote> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quote from StaffBookingQuote quote where quote.publicId = :publicId")
    Optional<StaffBookingQuote> findByPublicIdForUpdate(@Param("publicId") String publicId);
}

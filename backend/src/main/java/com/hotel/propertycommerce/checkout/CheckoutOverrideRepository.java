package com.hotel.propertycommerce.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckoutOverrideRepository extends JpaRepository<CheckoutOverride, Long> {

    List<CheckoutOverride> findByReservationIdOrderByCreatedAtAscIdAsc(Long reservationId);

    Optional<CheckoutOverride> findByIdAndHotelIdAndReservationId(
            Long id,
            Long hotelId,
            Long reservationId);
}

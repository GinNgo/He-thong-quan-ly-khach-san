package com.hotel.propertycommerce.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckoutOverrideRepository extends JpaRepository<CheckoutOverride, Long> {

    List<CheckoutOverride> findByReservationIdOrderByCreatedAtAscIdAsc(Long reservationId);
}

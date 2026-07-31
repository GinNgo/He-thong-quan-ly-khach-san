package com.hotel.propertycommerce.config;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropertyPaymentConfigurationRepository extends JpaRepository<PropertyPaymentConfiguration, Long> {
    @EntityGraph(attributePaths = "methods")
    Optional<PropertyPaymentConfiguration> findByHotelId(Long hotelId);
}

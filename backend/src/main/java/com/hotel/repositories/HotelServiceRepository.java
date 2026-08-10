package com.hotel.repositories;

import com.hotel.entities.HotelService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

@Repository
public interface HotelServiceRepository extends JpaRepository<HotelService, Long> {
    Optional<HotelService> findByHotelIdAndCodeIgnoreCase(Long hotelId, String code);

    // Use JPQL so Boolean/bit storage is handled by the configured JPA dialect.
    @Query("SELECT service FROM HotelService service " +
            "WHERE (service.hotel.id = :hotelId AND service.systemService = false) " +
            "OR (service.hotel IS NULL AND service.systemService = true) " +
            "ORDER BY service.systemService DESC, service.code")
    List<HotelService> findVisibleByHotelId(@Param("hotelId") Long hotelId);

    @Query(value = "SELECT * FROM services WHERE id = :id", nativeQuery = true)
    Optional<HotelService> findUnfilteredById(@Param("id") Long id);

    @Query(value = "SELECT COUNT(*) FROM services WHERE hotel_id = :hotelId AND LOWER(code) = LOWER(:code)", nativeQuery = true)
    long countByHotelIdAndCodeIgnoreCase(@Param("hotelId") Long hotelId, @Param("code") String code);

    @Query(value = "SELECT COUNT(*) FROM services WHERE hotel_id = :hotelId AND LOWER(code) = LOWER(:code) AND id <> :id", nativeQuery = true)
    long countByHotelIdAndCodeIgnoreCaseExcludingId(@Param("hotelId") Long hotelId, @Param("code") String code, @Param("id") Long id);
}

package com.hotel.repositories;

import com.hotel.entities.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    Optional<RoomType> findByCode(String code);
    java.util.List<RoomType> findByHotelId(Long hotelId);
    @Query("select roomType from RoomType roomType join fetch roomType.hotel hotel where hotel.id in :hotelIds order by hotel.id, roomType.basePrice, roomType.id")
    java.util.List<RoomType> findByHotelIdIn(@Param("hotelIds") java.util.Collection<Long> hotelIds);
    Optional<RoomType> findByCodeAndHotelId(String code, Long hotelId);
    long countByHotelId(Long hotelId);
    long countByHotelIdIn(java.util.Collection<Long> hotelIds);
    long countByIsDemoTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select roomType from RoomType roomType where roomType.id = :id")
    Optional<RoomType> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select roomType from RoomType roomType where roomType.id in :ids order by roomType.id")
    List<RoomType> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}

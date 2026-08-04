package com.hotel.repositories;

import com.hotel.entities.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PropertyImageRepository extends JpaRepository<PropertyImage, Long> {
    List<PropertyImage> findByHotelId(Long hotelId);
    List<PropertyImage> findByHotelIdOrderBySortOrderAscIdAsc(Long hotelId);
    @Query("select image from PropertyImage image join fetch image.hotel hotel where hotel.id in :hotelIds order by hotel.id, image.sortOrder, image.id")
    List<PropertyImage> findByHotelIdInOrderByHotelIdAscSortOrderAscIdAsc(@Param("hotelIds") Collection<Long> hotelIds);
    long countByHotelId(Long hotelId);
}

package com.hotel.repositories;

import com.hotel.entities.Amenity;
import com.hotel.entities.PropertyAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyAmenityRepository extends JpaRepository<PropertyAmenity, Long> {

    @Query("select assignment.amenity from PropertyAmenity assignment join assignment.amenity amenity "
            + "where assignment.hotel.id = :hotelId and amenity.status = 'ACTIVE' "
            + "order by amenity.category, amenity.sortOrder, amenity.nameVi")
    List<Amenity> findActiveAmenities(@Param("hotelId") Long hotelId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PropertyAmenity assignment where assignment.hotel.id = :hotelId")
    void deleteByHotelId(@Param("hotelId") Long hotelId);
}

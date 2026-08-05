package com.hotel.repositories;

import com.hotel.entities.Amenity;
import com.hotel.entities.PropertyAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PropertyAmenityRepository extends JpaRepository<PropertyAmenity, Long> {

    @Query("select assignment.amenity from PropertyAmenity assignment join assignment.amenity amenity "
            + "where assignment.hotel.id = :hotelId and amenity.status = 'ACTIVE' "
            + "order by amenity.category, amenity.sortOrder, amenity.nameVi")
    List<Amenity> findActiveAmenities(@Param("hotelId") Long hotelId);

    @Query(value = """
            select amenity_rows.hotel_id, amenity_rows.amenity_id, amenity_rows.name_vi
            from (
                select pa.hotel_id, a.id as amenity_id, a.name_vi, a.category, a.sort_order, 0 as source_order
                from property_amenities pa
                join amenities a on a.id = pa.amenity_id
                where pa.hotel_id in (:hotelIds) and a.status = 'ACTIVE'
                union all
                select rta.hotel_id, a.id as amenity_id, a.name_vi, a.category, a.sort_order, 1 as source_order
                from room_type_amenities rta
                join room_types rt on rt.id = rta.room_type_id
                join amenities a on a.id = rta.amenity_id
                where rta.hotel_id in (:hotelIds) and rt.status = 'ACTIVE' and a.status = 'ACTIVE'
            ) amenity_rows
            order by amenity_rows.hotel_id, amenity_rows.category, amenity_rows.sort_order,
                     amenity_rows.name_vi, amenity_rows.source_order
            """, nativeQuery = true)
    List<Object[]> findPublicAmenityNamesByHotelIds(@Param("hotelIds") Collection<Long> hotelIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PropertyAmenity assignment where assignment.hotel.id = :hotelId")
    void deleteByHotelId(@Param("hotelId") Long hotelId);
}

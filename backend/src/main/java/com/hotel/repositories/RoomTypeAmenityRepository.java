package com.hotel.repositories;

import com.hotel.entities.Amenity;
import com.hotel.entities.RoomTypeAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomTypeAmenityRepository extends JpaRepository<RoomTypeAmenity, Long> {

    @Query("select assignment.amenity from RoomTypeAmenity assignment join assignment.amenity amenity "
            + "where assignment.roomType.id = :roomTypeId and amenity.status = 'ACTIVE' "
            + "order by amenity.category, amenity.sortOrder, amenity.nameVi")
    List<Amenity> findActiveAmenitiesByRoomTypeId(@Param("roomTypeId") Long roomTypeId);

    @Query("select distinct assignment.amenity from RoomTypeAmenity assignment "
            + "join assignment.amenity amenity join assignment.roomType roomType "
            + "where assignment.hotel.id = :hotelId and roomType.status = 'ACTIVE' and amenity.status = 'ACTIVE' "
            + "order by amenity.category, amenity.sortOrder, amenity.nameVi")
    List<Amenity> findActiveAmenitiesByHotelId(@Param("hotelId") Long hotelId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RoomTypeAmenity assignment where assignment.roomType.id = :roomTypeId")
    void deleteByRoomTypeId(@Param("roomTypeId") Long roomTypeId);
}

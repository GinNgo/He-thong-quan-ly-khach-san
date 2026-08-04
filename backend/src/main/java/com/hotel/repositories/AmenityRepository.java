package com.hotel.repositories;

import com.hotel.entities.Amenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, Long> {
    Optional<Amenity> findByCode(String code);
    List<Amenity> findByStatusOrderByCategoryAscSortOrderAscNameViAsc(String status);
    List<Amenity> findAllByOrderByCategoryAscSortOrderAscNameViAsc();
    List<Amenity> findByIdInAndStatus(Collection<Long> ids, String status);
}

package com.hotel.repositories;

import com.hotel.entities.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByParentId(Long parentId);
    List<Location> findByParentIsNull();
    java.util.Optional<Location> findByCode(String code);
    
    @org.springframework.data.jpa.repository.Query("SELECT l FROM Location l WHERE l.locationType = :type AND (l.normalizedName LIKE %:keyword% OR LOWER(l.nameVi) LIKE %:keyword% OR LOWER(l.legacyParentName) LIKE %:keyword%)")
    org.springframework.data.domain.Page<Location> searchLocations(@org.springframework.data.repository.query.Param("keyword") String keyword, @org.springframework.data.repository.query.Param("type") String type, org.springframework.data.domain.Pageable pageable);
}

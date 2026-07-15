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
    java.util.Optional<Location> findByLocationTypeAndSourceCode(String locationType, String sourceCode);
    List<Location> findByLocationTypeAndStatusOrderBySortOrderAscNameViAsc(String locationType, String status);
    List<Location> findByParentIdAndLocationTypeAndStatusOrderByNameViAsc(Long parentId, String locationType, String status);
    
    @org.springframework.data.jpa.repository.Query("SELECT l FROM Location l WHERE l.status = 'ACTIVE' AND (:type IS NULL OR l.locationType = :type) AND (l.normalizedName LIKE CONCAT('%', :keyword, '%') OR LOWER(l.fullPath) LIKE CONCAT('%', LOWER(:rawKeyword), '%'))")
    org.springframework.data.domain.Page<Location> searchLocations(@org.springframework.data.repository.query.Param("keyword") String keyword, @org.springframework.data.repository.query.Param("rawKeyword") String rawKeyword, @org.springframework.data.repository.query.Param("type") String type, org.springframework.data.domain.Pageable pageable);
}

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
    
    @org.springframework.data.jpa.repository.Query("""
            SELECT l FROM Location l
            WHERE l.status = 'ACTIVE'
              AND (:type IS NULL OR l.locationType = :type)
              AND (l.normalizedName LIKE CONCAT('%', :keyword, '%')
                   OR LOWER(l.fullPath) LIKE CONCAT('%', LOWER(:rawKeyword), '%'))
            ORDER BY CASE
                       WHEN l.normalizedName = :keyword THEN 0
                       WHEN l.normalizedName LIKE CONCAT(:keyword, '%') THEN 1
                       ELSE 2
                     END,
                     COALESCE(l.sortOrder, 0), l.nameVi, l.id
            """)
    org.springframework.data.domain.Page<Location> searchLocations(@org.springframework.data.repository.query.Param("keyword") String keyword, @org.springframework.data.repository.query.Param("rawKeyword") String rawKeyword, @org.springframework.data.repository.query.Param("type") String type, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            SELECT l FROM Location l
            LEFT JOIN l.parent p
            LEFT JOIN p.parent pp
            WHERE l.locationType = 'LANDMARK'
              AND l.status = 'ACTIVE'
              AND l.latitude IS NOT NULL
              AND l.longitude IS NOT NULL
              AND (:provinceId IS NULL OR p.id = :provinceId OR pp.id = :provinceId)
              AND (l.normalizedName LIKE CONCAT('%', :keyword, '%')
                   OR LOWER(l.nameEn) LIKE CONCAT('%', LOWER(:rawKeyword), '%')
                   OR LOWER(l.fullPath) LIKE CONCAT('%', LOWER(:rawKeyword), '%'))
            ORDER BY CASE
                       WHEN l.normalizedName = :keyword THEN 0
                       WHEN l.normalizedName LIKE CONCAT(:keyword, '%') THEN 1
                       ELSE 2
                     END,
                     COALESCE(l.popularityScore, 0) DESC, l.nameVi, l.id
            """)
    org.springframework.data.domain.Page<Location> searchActiveLandmarks(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("rawKeyword") String rawKeyword,
            @org.springframework.data.repository.query.Param("provinceId") Long provinceId,
            org.springframework.data.domain.Pageable pageable);
}

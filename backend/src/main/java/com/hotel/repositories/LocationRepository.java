package com.hotel.repositories;

import com.hotel.entities.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByParentId(Long parentId);
    List<Location> findByParentIsNull();
    Optional<Location> findByCode(String code);
    Optional<Location> findByLocationTypeAndSourceCode(String locationType, String sourceCode);
    Optional<Location> findByIdAndLocationType(Long id, String locationType);
    List<Location> findByLocationTypeAndStatusOrderBySortOrderAscNameViAsc(String locationType, String status);
    List<Location> findByLocationTypeAndStatusAndSourceCodeStartingWithOrderBySortOrderAscNameViAsc(
            String locationType, String status, String sourceCodePrefix);
    List<Location> findByLocationTypeAndSourceCodeIn(String locationType, Collection<String> sourceCodes);
    List<Location> findByParentIdAndLocationTypeAndStatusOrderByNameViAsc(Long parentId, String locationType, String status);
    List<Location> findByParentIdInAndLocationTypeAndStatusOrderByNameViAsc(
            Collection<Long> parentIds, String locationType, String status);

    @Query("SELECT l FROM Location l WHERE l.status = 'ACTIVE' ORDER BY l.nameVi")
    List<Location> findActiveSearchCandidates(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT l FROM Location l WHERE l.status = 'ACTIVE' AND (:type IS NULL OR l.locationType = :type) AND (l.normalizedName LIKE CONCAT('%', :keyword, '%') OR LOWER(l.fullPath) LIKE CONCAT('%', LOWER(:rawKeyword), '%'))")
    org.springframework.data.domain.Page<Location> searchLocations(@Param("keyword") String keyword,
                                                                   @Param("rawKeyword") String rawKeyword,
                                                                   @Param("type") String type,
                                                                   org.springframework.data.domain.Pageable pageable);

    @Query("SELECT l FROM Location l WHERE l.status = 'ACTIVE' AND l.locationType = 'PROVINCE' AND l.sourceCode LIKE 'VN34-%' AND (l.normalizedName LIKE CONCAT('%', :keyword, '%') OR LOWER(l.fullPath) LIKE CONCAT('%', LOWER(:rawKeyword), '%'))")
    org.springframework.data.domain.Page<Location> searchCurrentProvinces(@Param("keyword") String keyword,
                                                                          @Param("rawKeyword") String rawKeyword,
                                                                          org.springframework.data.domain.Pageable pageable);

    @Query("""
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
            ORDER BY COALESCE(l.popularityScore, 0) DESC, l.nameVi ASC
            """)
    org.springframework.data.domain.Page<Location> searchActiveLandmarks(@Param("keyword") String keyword,
                                                                         @Param("rawKeyword") String rawKeyword,
                                                                         @Param("provinceId") Long provinceId,
                                                                         org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT l FROM Location l
            LEFT JOIN l.parent p
            LEFT JOIN p.parent pp
            WHERE l.locationType = 'LANDMARK'
              AND l.status = 'ACTIVE'
              AND l.latitude IS NOT NULL
              AND l.longitude IS NOT NULL
              AND (p.id IN :provinceIds OR pp.id IN :provinceIds)
              AND (l.normalizedName LIKE CONCAT('%', :keyword, '%')
                   OR LOWER(l.nameEn) LIKE CONCAT('%', LOWER(:rawKeyword), '%')
                   OR LOWER(l.fullPath) LIKE CONCAT('%', LOWER(:rawKeyword), '%'))
            ORDER BY COALESCE(l.popularityScore, 0) DESC, l.nameVi ASC
            """)
    org.springframework.data.domain.Page<Location> searchActiveLandmarksInProvinceScope(
            @Param("keyword") String keyword,
            @Param("rawKeyword") String rawKeyword,
            @Param("provinceIds") Collection<Long> provinceIds,
            org.springframework.data.domain.Pageable pageable);
}

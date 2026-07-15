package com.hotel.repositories;

import com.hotel.entities.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Pageable;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    java.util.Optional<Hotel> findByCode(String code);
    java.util.Optional<Hotel> findBySeedKey(String seedKey);
    long countByIsDemoTrue();
    java.util.List<Hotel> findByIsDemoTrue();
    List<Hotel> findByAddressLineContainingIgnoreCaseAndStatus(String addressLine, String status);
    List<Hotel> findByStatus(String status);
    long countByProvinceIdAndApprovalStatusAndOperationStatus(Long provinceId, String approvalStatus, String operationStatus);
    long countByWardIdAndApprovalStatusAndOperationStatus(Long wardId, String approvalStatus, String operationStatus);
    long countByProvinceIdAndApprovalStatusAndOperationStatusAndIsDemoFalse(Long provinceId, String approvalStatus, String operationStatus);
    long countByWardIdAndApprovalStatusAndOperationStatusAndIsDemoFalse(Long wardId, String approvalStatus, String operationStatus);
    @org.springframework.data.jpa.repository.Query("""
            SELECT h FROM Hotel h
            WHERE h.approvalStatus = 'APPROVED' AND h.operationStatus = 'ACTIVE'
              AND (h.normalizedName LIKE CONCAT('%', :keyword, '%')
                OR h.normalizedAddress LIKE CONCAT('%', :keyword, '%')
                OR LOWER(h.code) LIKE CONCAT('%', LOWER(:rawKeyword), '%')
                OR LOWER(h.slug) LIKE CONCAT('%', LOWER(:rawKeyword), '%')
                OR h.phone LIKE CONCAT('%', :rawKeyword, '%'))
            ORDER BY h.nameVi, h.name
            """)
    List<Hotel> searchAutocomplete(@org.springframework.data.repository.query.Param("keyword") String keyword,
                                   @org.springframework.data.repository.query.Param("rawKeyword") String rawKeyword,
                                   Pageable pageable);
    @org.springframework.data.jpa.repository.Query("SELECT up.hotel FROM UserProperty up WHERE up.user.id = :ownerId AND up.relationshipType = 'OWNER'")
    List<Hotel> findByOwnerId(@org.springframework.data.repository.query.Param("ownerId") Long ownerId);
}

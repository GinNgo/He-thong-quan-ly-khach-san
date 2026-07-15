package com.hotel.repositories;

import com.hotel.entities.PropertyClaimRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyClaimRequestRepository extends JpaRepository<PropertyClaimRequest, Long> {
    List<PropertyClaimRequest> findByPropertyId(Long propertyId);
    Page<PropertyClaimRequest> findByStatus(String status, Pageable pageable);
    boolean existsByPropertyIdAndRequesterUserIdAndStatus(Long propertyId, Long requesterUserId, String status);
    Optional<PropertyClaimRequest> findFirstByRequesterUserIdOrderByCreatedAtDesc(Long requesterUserId);
}

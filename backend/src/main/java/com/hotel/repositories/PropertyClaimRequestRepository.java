package com.hotel.repositories;

import com.hotel.entities.PropertyClaimRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyClaimRequestRepository extends JpaRepository<PropertyClaimRequest, Long> {
    List<PropertyClaimRequest> findByPropertyId(Long propertyId);
    Page<PropertyClaimRequest> findByStatus(String status, Pageable pageable);
    boolean existsByPropertyIdAndRequesterUserIdAndStatus(Long propertyId, Long requesterUserId, String status);
    Optional<PropertyClaimRequest> findFirstByRequesterUserIdOrderByCreatedAtDesc(Long requesterUserId);
    long countByRequesterUserIdAndCreatedAtGreaterThan(Long requesterUserId, LocalDateTime cutoff);
    Optional<PropertyClaimRequest> findFirstByRequesterUserIdAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
            Long requesterUserId,
            LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select claim
            from PropertyClaimRequest claim
            join fetch claim.property
            join fetch claim.requesterUser
            where claim.id = :claimId
            """)
    Optional<PropertyClaimRequest> findByIdForUpdate(@Param("claimId") Long claimId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select claim
            from PropertyClaimRequest claim
            join fetch claim.property
            join fetch claim.requesterUser
            where claim.id = :claimId
              and claim.requesterUser.id = :requesterUserId
            """)
    Optional<PropertyClaimRequest> findByIdAndRequesterUserIdForUpdate(
            @Param("claimId") Long claimId,
            @Param("requesterUserId") Long requesterUserId);

    @Query("""
            select claim
            from PropertyClaimRequest claim
            join fetch claim.property
            join fetch claim.requesterUser
            where claim.requesterUser.id = :requesterUserId
              and claim.property.id in :propertyIds
            order by claim.createdAt desc, claim.id desc
            """)
    List<PropertyClaimRequest> findByRequesterAndPropertiesOrderByCreatedAtDescIdDesc(
            @Param("requesterUserId") Long requesterUserId,
            @Param("propertyIds") Collection<Long> propertyIds);

    @Query("""
            select claim
            from PropertyClaimRequest claim
            join fetch claim.property
            join fetch claim.requesterUser
            where claim.requesterUser.id = :requesterUserId
              and claim.property.id in :propertyIds
              and claim.status = :status
            order by claim.reviewedAt desc, claim.id desc
            """)
    List<PropertyClaimRequest> findByRequesterAndPropertiesAndStatus(
            @Param("requesterUserId") Long requesterUserId,
            @Param("propertyIds") Collection<Long> propertyIds,
            @Param("status") String status);
}

package com.hotel.repositories;

import com.hotel.entities.RefreshTokenSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {

    @Query("select token from RefreshTokenSession token where token.tokenHash = :tokenHash")
    Optional<RefreshTokenSession> findStoredByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenSession token where token.id = :id")
    Optional<RefreshTokenSession> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshTokenSession token
               set token.status = 'REVOKED',
                   token.revokedAt = :revokedAt,
                   token.revocationReason = :reason
             where token.familyId = :familyId
               and token.status = 'ACTIVE'
            """)
    int revokeActiveFamily(@Param("familyId") String familyId,
                           @Param("revokedAt") Instant revokedAt,
                           @Param("reason") String reason);

    long countByFamilyIdAndStatus(String familyId, String status);
}

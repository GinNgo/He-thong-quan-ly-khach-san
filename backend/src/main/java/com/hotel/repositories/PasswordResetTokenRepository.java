package com.hotel.repositories;

import com.hotel.entities.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token left join fetch token.user where token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long countByRequestIpAndRequestedAtAfter(String requestIp, Instant requestedAfter);

    long countByEmailFingerprintAndRequestedAtAfter(String emailFingerprint, Instant requestedAfter);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken token
               set token.revokedAt = :revokedAt
             where token.user.id = :userId
               and token.usedAt is null
               and token.revokedAt is null
            """)
    int revokeActiveForUser(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken token
               set token.revokedAt = :revokedAt
             where token.emailFingerprint = :emailFingerprint
               and token.usedAt is null
               and token.revokedAt is null
            """)
    int revokeActiveForFingerprint(@Param("emailFingerprint") String emailFingerprint,
                                   @Param("revokedAt") Instant revokedAt);
}

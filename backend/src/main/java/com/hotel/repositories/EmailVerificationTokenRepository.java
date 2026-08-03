package com.hotel.repositories;

import com.hotel.entities.EmailVerificationPurpose;
import com.hotel.entities.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationToken token where token.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long countByUser_IdAndRequestedAtAfter(Long userId, Instant requestedAfter);

    long countByRequestIpAndRequestedAtAfter(String requestIp, Instant requestedAfter);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailVerificationToken token
               set token.revokedAt = :revokedAt
             where token.user.id = :userId
               and token.purpose = :purpose
               and token.usedAt is null
               and token.revokedAt is null
            """)
    int revokeActiveForUserAndPurpose(
            @Param("userId") Long userId,
            @Param("purpose") EmailVerificationPurpose purpose,
            @Param("revokedAt") Instant revokedAt);
}

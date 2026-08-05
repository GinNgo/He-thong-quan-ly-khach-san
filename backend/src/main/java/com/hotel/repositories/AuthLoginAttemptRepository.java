package com.hotel.repositories;

import com.hotel.entities.AuthLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, Long> {

    long countByAccountFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(
            String accountFingerprint, String outcome, Instant occurredAt);

    long countByIpFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(
            String ipFingerprint, String outcome, Instant occurredAt);
}

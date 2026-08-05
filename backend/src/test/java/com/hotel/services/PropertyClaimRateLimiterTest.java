package com.hotel.services;

import com.hotel.entities.PropertyClaimRequest;
import com.hotel.exceptions.PropertyClaimRateLimitException;
import com.hotel.repositories.PropertyClaimRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyClaimRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");

    @Mock private PropertyClaimRequestRepository claimRepository;

    private PropertyClaimRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new PropertyClaimRateLimiter(
                claimRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                Duration.ofMinutes(15));
    }

    @Test
    void belowLimitIsAllowedWithoutOldestLookup() {
        when(claimRepository.countByRequesterUserIdAndCreatedAtGreaterThan(
                42L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(15)))
                .thenReturn(2L);

        assertDoesNotThrow(() -> rateLimiter.check(42L));

        verify(claimRepository, never())
                .findFirstByRequesterUserIdAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                        any(), any());
    }

    @Test
    void exactLimitIsBlockedWithDeterministicRetrySeconds() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        LocalDateTime cutoff = now.minusMinutes(15);
        PropertyClaimRequest oldest = new PropertyClaimRequest();
        oldest.setCreatedAt(now.minusMinutes(10).minusNanos(1));
        when(claimRepository.countByRequesterUserIdAndCreatedAtGreaterThan(42L, cutoff))
                .thenReturn(3L);
        when(claimRepository
                .findFirstByRequesterUserIdAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(42L, cutoff))
                .thenReturn(Optional.of(oldest));

        PropertyClaimRateLimitException exception = assertThrows(
                PropertyClaimRateLimitException.class,
                () -> rateLimiter.check(42L));

        assertEquals("PROPERTY_CLAIM_RATE_LIMITED", PropertyClaimRateLimitException.ERROR_CODE);
        assertEquals(300, exception.getRetryAfterSeconds());
    }

    @Test
    void requestAtExactWindowBoundaryIsExpired() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(15);
        when(claimRepository.countByRequesterUserIdAndCreatedAtGreaterThan(42L, cutoff))
                .thenReturn(2L);

        assertDoesNotThrow(() -> rateLimiter.check(42L));

        verify(claimRepository).countByRequesterUserIdAndCreatedAtGreaterThan(42L, cutoff);
    }
}

package com.hotel.services;

import com.hotel.entities.PropertyClaimRequest;
import com.hotel.exceptions.PropertyClaimRateLimitException;
import com.hotel.repositories.PropertyClaimRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class PropertyClaimRateLimiter {

    private final PropertyClaimRequestRepository claimRepository;
    private final Clock clock;
    private final long maxRequests;
    private final Duration window;

    @Autowired
    public PropertyClaimRateLimiter(
            PropertyClaimRequestRepository claimRepository,
            @Value("${app.property-claim.rate-limit.max-requests:3}") long maxRequests,
            @Value("${app.property-claim.rate-limit.window-minutes:15}") long windowMinutes) {
        this(claimRepository, Clock.systemDefaultZone(), maxRequests, Duration.ofMinutes(windowMinutes));
    }

    PropertyClaimRateLimiter(
            PropertyClaimRequestRepository claimRepository,
            Clock clock,
            long maxRequests,
            Duration window) {
        this.claimRepository = claimRepository;
        this.clock = clock;
        this.maxRequests = Math.max(1, maxRequests);
        this.window = window == null || window.isZero() || window.isNegative()
                ? Duration.ofMinutes(15)
                : window;
    }

    public void check(Long requesterUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minus(window);
        long acceptedRequests = claimRepository
                .countByRequesterUserIdAndCreatedAtGreaterThan(requesterUserId, cutoff);
        if (acceptedRequests < maxRequests) return;

        long retryAfterSeconds = claimRepository
                .findFirstByRequesterUserIdAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                        requesterUserId, cutoff)
                .map(PropertyClaimRequest::getCreatedAt)
                .map(oldest -> secondsUntil(now, oldest.plus(window)))
                .orElseGet(window::toSeconds);
        throw new PropertyClaimRateLimitException(retryAfterSeconds);
    }

    private long secondsUntil(LocalDateTime now, LocalDateTime availableAt) {
        Duration remaining = Duration.between(now, availableAt);
        long seconds = remaining.getSeconds();
        if (remaining.getNano() > 0) seconds++;
        return Math.max(1, seconds);
    }
}

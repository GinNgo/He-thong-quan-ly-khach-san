package com.hotel.services;

import com.hotel.entities.User;
import com.hotel.repositories.RefreshTokenSessionRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Coordinates logout invalidation across access tokens and refresh-token families. */
@Service
public class AuthSessionRevocationService {

    private final UserRepository userRepository;
    private final RefreshTokenSessionRepository refreshTokenRepository;
    private final Clock clock;

    @Autowired
    public AuthSessionRevocationService(
            UserRepository userRepository,
            RefreshTokenSessionRepository refreshTokenRepository) {
        this(userRepository, refreshTokenRepository, Clock.systemUTC());
    }

    AuthSessionRevocationService(
            UserRepository userRepository,
            RefreshTokenSessionRepository refreshTokenRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void revokeUserSession(Long userId, String reason) {
        if (userId == null) return;
        Instant revokedAt = clock.instant();
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) return;
        user.setAuthRevokedAt(revokedAt);
        userRepository.save(user);
        refreshTokenRepository.revokeActiveForUser(userId, revokedAt, reason);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findUserId(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return userRepository.findByUsername(username.strip().toLowerCase(java.util.Locale.ROOT))
                .map(User::getId);
    }

    public Instant now() {
        return clock.instant();
    }
}

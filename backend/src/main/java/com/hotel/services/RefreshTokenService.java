package com.hotel.services;

import com.hotel.entities.RefreshTokenSession;
import com.hotel.entities.User;
import com.hotel.repositories.RefreshTokenSessionRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.AccountDisabledAuthenticationException;
import com.hotel.security.AccountStatusPolicy;
import com.hotel.security.RefreshTokenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenSessionRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Duration refreshLifetime;

    @Autowired
    public RefreshTokenService(
            RefreshTokenSessionRepository repository,
            UserRepository userRepository,
            @Value("${jwt.refresh-expiration:604800000}") long refreshExpirationMs) {
        this(repository, userRepository, Clock.systemUTC(), new SecureRandom(), refreshExpirationMs);
    }

    RefreshTokenService(
            RefreshTokenSessionRepository repository,
            UserRepository userRepository,
            Clock clock,
            SecureRandom secureRandom,
            long refreshExpirationMs) {
        if (refreshExpirationMs <= 0) {
            throw new IllegalArgumentException("JWT refresh expiration must be positive.");
        }
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.refreshLifetime = Duration.ofMillis(refreshExpirationMs);
    }

    @Transactional
    public RefreshGrant issueForUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(RefreshTokenException::invalid);
        AccountStatusPolicy.requireActive(user);

        Instant now = clock.instant();
        String rawToken = generateRawToken();
        RefreshTokenSession session = new RefreshTokenSession(
                user,
                UUID.randomUUID().toString(),
                hashToken(rawToken),
                now,
                now.plus(refreshLifetime));
        repository.saveAndFlush(session);
        return new RefreshGrant(user.getId(), rawToken, session.getExpiresAt());
    }

    @Transactional(noRollbackFor = {
            RefreshTokenException.class,
            AccountDisabledAuthenticationException.class
    })
    public RefreshGrant rotate(String rawToken) {
        if (rawToken == null || rawToken.length() < 32 || rawToken.length() > 512) {
            throw RefreshTokenException.invalid();
        }

        String tokenHash = hashToken(rawToken);
        RefreshTokenSession candidate = repository.findStoredByTokenHash(tokenHash)
                .orElseThrow(RefreshTokenException::invalid);
        RefreshTokenSession current = repository.findByIdForUpdate(candidate.getId())
                .orElseThrow(RefreshTokenException::invalid);
        Instant now = clock.instant();

        if (current.isExpiredAt(now)) {
            current.expire(now);
            repository.save(current);
            throw RefreshTokenException.expired();
        }

        if (!RefreshTokenSession.ACTIVE.equals(current.getStatus())) {
            if (RefreshTokenSession.ROTATED.equals(current.getStatus())) {
                current.recordReuse(now);
                repository.save(current);
                repository.revokeActiveFamily(current.getFamilyId(), now, "REUSE_DETECTED");
                throw RefreshTokenException.reused();
            }
            throw RefreshTokenException.invalid();
        }

        if (!AccountStatusPolicy.isActive(current.getUser().getStatus())) {
            repository.revokeActiveFamily(current.getFamilyId(), now, "ACCOUNT_DISABLED");
            throw new AccountDisabledAuthenticationException();
        }

        String replacement = generateRawToken();
        String replacementHash = hashToken(replacement);
        current.rotate(replacementHash, now);
        repository.save(current);
        repository.saveAndFlush(new RefreshTokenSession(
                current.getUser(),
                current.getFamilyId(),
                replacementHash,
                now,
                current.getExpiresAt()));

        return new RefreshGrant(current.getUser().getId(), replacement, current.getExpiresAt());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record RefreshGrant(Long userId, String rawToken, Instant expiresAt) {
    }
}

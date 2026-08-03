package com.hotel.services;

import com.hotel.entities.RefreshTokenSession;
import com.hotel.entities.User;
import com.hotel.repositories.RefreshTokenSessionRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.RefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Mock
    private RefreshTokenSessionRepository repository;

    @Mock
    private UserRepository userRepository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setUsername("refresh-user");
        user.setStatus("ACTIVE");
        service = new RefreshTokenService(
                repository,
                userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new java.security.SecureRandom(),
                86_400_000L);
        lenient().when(repository.save(any(RefreshTokenSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(repository.saveAndFlush(any(RefreshTokenSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issueStoresOnlyAHashAndReturnsRawTokenOnce() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        RefreshTokenService.RefreshGrant grant = service.issueForUser(42L);

        assertEquals(42L, grant.userId());
        assertNotEquals(grant.rawToken(), RefreshTokenService.hashToken(grant.rawToken()));
        verify(repository).saveAndFlush(any(RefreshTokenSession.class));
    }

    @Test
    void rotateMarksThePresentedTokenRotatedAndIssuesAChild() {
        String raw = "a".repeat(43);
        RefreshTokenSession current = new RefreshTokenSession(
                user, "family-1", RefreshTokenService.hashToken(raw), NOW.minusSeconds(10), NOW.plusSeconds(3600));
        when(repository.findStoredByTokenHash(RefreshTokenService.hashToken(raw)))
                .thenReturn(Optional.of(current));
        when(repository.findByIdForUpdate(current.getId()))
                .thenReturn(Optional.of(current));

        RefreshTokenService.RefreshGrant grant = service.rotate(raw);

        assertEquals(42L, grant.userId());
        assertEquals(RefreshTokenSession.ROTATED, current.getStatus());
        assertNotEquals(raw, grant.rawToken());
        verify(repository).saveAndFlush(any(RefreshTokenSession.class));
    }

    @Test
    void replayRevokesTheWholeFamily() {
        String raw = "b".repeat(43);
        RefreshTokenSession replayed = new RefreshTokenSession(
                user, "family-reuse", RefreshTokenService.hashToken(raw), NOW.minusSeconds(10), NOW.plusSeconds(3600));
        replayed.rotate("replacement-hash", NOW.minusSeconds(1));
        when(repository.findStoredByTokenHash(RefreshTokenService.hashToken(raw)))
                .thenReturn(Optional.of(replayed));
        when(repository.findByIdForUpdate(replayed.getId()))
                .thenReturn(Optional.of(replayed));

        RefreshTokenException exception = assertThrows(RefreshTokenException.class, () -> service.rotate(raw));

        assertEquals("REFRESH_TOKEN_REUSED", exception.getCode());
        verify(repository).revokeActiveFamily("family-reuse", NOW, "REUSE_DETECTED");
    }

    @Test
    void expiredTokenCannotBeRotated() {
        String raw = "c".repeat(43);
        RefreshTokenSession expired = new RefreshTokenSession(
                user, "family-expired", RefreshTokenService.hashToken(raw), NOW.minusSeconds(3600), NOW.minusSeconds(1));
        when(repository.findStoredByTokenHash(RefreshTokenService.hashToken(raw)))
                .thenReturn(Optional.of(expired));
        when(repository.findByIdForUpdate(expired.getId()))
                .thenReturn(Optional.of(expired));

        RefreshTokenException exception = assertThrows(RefreshTokenException.class, () -> service.rotate(raw));

        assertEquals("REFRESH_TOKEN_EXPIRED", exception.getCode());
        assertEquals(RefreshTokenSession.EXPIRED, expired.getStatus());
    }
}

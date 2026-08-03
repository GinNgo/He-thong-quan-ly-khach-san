package com.hotel.services;

import com.hotel.entities.PasswordResetToken;
import com.hotel.entities.User;
import com.hotel.repositories.PasswordResetTokenRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.PasswordResetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AuthSessionRevocationService authSessionRevocationService;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setEmail("guest@example.com");
        user.setFullName("Guest");
        user.setPasswordHash("old-hash");
        service = new PasswordResetService(
                tokenRepository,
                userRepository,
                passwordEncoder,
                emailService,
                authSessionRevocationService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new java.security.SecureRandom(),
                30,
                15,
                5,
                3,
                "https://luxestay.example/reset-password");
        lenient().when(tokenRepository.countByRequestIpAndRequestedAtAfter(anyString(), any())).thenReturn(0L);
        lenient().when(tokenRepository.countByEmailFingerprintAndRequestedAtAfter(anyString(), any())).thenReturn(0L);
        lenient().when(tokenRepository.saveAndFlush(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestCreatesHashedTokenAndSendsLinkOnlyForExistingAccount() {
        when(userRepository.findByEmailIgnoreCase("guest@example.com")).thenReturn(Optional.of(user));

        service.requestReset(" Guest@Example.com ", "127.0.0.1");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).saveAndFlush(tokenCaptor.capture());
        PasswordResetToken stored = tokenCaptor.getValue();
        assertEquals(42L, stored.getUser().getId());
        assertEquals(NOW.plusSeconds(1800), stored.getExpiresAt());
        assertEquals(64, stored.getTokenHash().length());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(
                anyString(), anyString(), urlCaptor.capture(), anyLong());
        String rawToken = URLDecoder.decode(
                urlCaptor.getValue().substring(urlCaptor.getValue().indexOf("token=") + 6),
                StandardCharsets.UTF_8);
        assertEquals(stored.getTokenHash(), PasswordResetService.hashToken(rawToken));
    }

    @Test
    void unknownAccountUsesSameRequestPathWithoutSendingEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.requestReset("missing@example.com", "127.0.0.1");

        verify(tokenRepository).saveAndFlush(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void rateLimitStopsAdditionalRequestsBeforeCreatingToken() {
        when(tokenRepository.countByRequestIpAndRequestedAtAfter(anyString(), any())).thenReturn(5L);

        service.requestReset("guest@example.com", "127.0.0.1");

        verify(tokenRepository, never()).saveAndFlush(any(PasswordResetToken.class));
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void resetChangesPasswordAndRevokesExistingSessions() {
        String rawToken = "valid-reset-token";
        PasswordResetToken stored = token(rawToken, NOW.plusSeconds(1800));
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken(rawToken)))
                .thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.resetPassword(rawToken, "new-password");

        assertNotNull(stored.getUsedAt());
        assertEquals("new-hash", user.getPasswordHash());
        verify(authSessionRevocationService).revokeUserSession(42L, "PASSWORD_RESET");
    }

    @Test
    void expiredTokenCannotBeUsed() {
        String rawToken = "expired-reset-token";
        PasswordResetToken stored = token(rawToken, NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken(rawToken)))
                .thenReturn(Optional.of(stored));

        PasswordResetException exception = assertThrows(
                PasswordResetException.class,
                () -> service.resetPassword(rawToken, "new-password"));

        assertEquals(PasswordResetException.EXPIRED_TOKEN_CODE, exception.getCode());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void replayedTokenCannotBeUsedAgain() {
        String rawToken = "replayed-reset-token";
        PasswordResetToken stored = token(rawToken, NOW.plusSeconds(1800));
        stored.setUsedAt(NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken(rawToken)))
                .thenReturn(Optional.of(stored));

        PasswordResetException exception = assertThrows(
                PasswordResetException.class,
                () -> service.resetPassword(rawToken, "new-password"));

        assertEquals(PasswordResetException.INVALID_TOKEN_CODE, exception.getCode());
        verify(passwordEncoder, never()).encode(anyString());
    }

    private PasswordResetToken token(String rawToken, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setEmailFingerprint(PasswordResetService.hashToken(user.getEmail()));
        token.setTokenHash(PasswordResetService.hashToken(rawToken));
        token.setRequestedAt(NOW);
        token.setExpiresAt(expiresAt);
        token.setRequestIp("127.0.0.1");
        return token;
    }
}

package com.hotel.services;

import com.hotel.entities.EmailVerificationPurpose;
import com.hotel.entities.EmailVerificationToken;
import com.hotel.entities.User;
import com.hotel.repositories.EmailVerificationTokenRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.EmailVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationMailer mailer;
    @Mock private AuthSessionRevocationService authSessionRevocationService;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        SecureRandom deterministicRandom = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 7);
            }
        };
        service = new EmailVerificationService(
                tokenRepository,
                userRepository,
                mailer,
                authSessionRevocationService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                deterministicRandom,
                60,
                15,
                8,
                3,
                "https://luxestay.test/verify-email");
        lenient().when(tokenRepository.saveAndFlush(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void initialVerificationStoresOnlyHashAndSendsOneTimeLink() {
        User user = user(1L, "guest@example.com", false);
        when(mailer.send(anyString(), anyString(), anyString(), any(), anyLong())).thenReturn(true);

        boolean sent = service.requestInitialVerification(user);

        assertTrue(sent);
        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).saveAndFlush(tokenCaptor.capture());
        EmailVerificationToken token = tokenCaptor.getValue();
        assertEquals(EmailVerificationPurpose.INITIAL_VERIFICATION, token.getPurpose());
        assertEquals("guest@example.com", token.getTargetEmail());
        assertEquals(64, token.getTokenHash().length());
        assertEquals(NOW.plusSeconds(3600), token.getExpiresAt());
        verify(mailer).send(
                "guest@example.com",
                "Guest",
                "https://luxestay.test/verify-email?token=BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc",
                EmailVerificationPurpose.INITIAL_VERIFICATION,
                60);
    }

    @Test
    void confirmationMarksInitialEmailVerifiedAndRejectsReplay() {
        User user = user(2L, "guest@example.com", false);
        EmailVerificationToken token = token(user, EmailVerificationPurpose.INITIAL_VERIFICATION,
                "guest@example.com", NOW.plusSeconds(60));
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken("raw-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user));

        EmailVerificationService.ConfirmationResult result = service.confirm("raw-token");

        assertFalse(result.emailChanged());
        assertEquals(NOW, user.getEmailVerifiedAt());
        assertEquals(NOW, token.getUsedAt());
        EmailVerificationException replay = assertThrows(
                EmailVerificationException.class,
                () -> service.confirm("raw-token"));
        assertEquals("EMAIL_VERIFICATION_TOKEN_INVALID", replay.getCode());
    }

    @Test
    void expiredTokenIsRevokedWithoutChangingUser() {
        User user = user(3L, "guest@example.com", false);
        EmailVerificationToken token = token(user, EmailVerificationPurpose.INITIAL_VERIFICATION,
                "guest@example.com", NOW);
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken("expired")))
                .thenReturn(Optional.of(token));

        EmailVerificationException exception = assertThrows(
                EmailVerificationException.class,
                () -> service.confirm("expired"));

        assertEquals("EMAIL_VERIFICATION_TOKEN_EXPIRED", exception.getCode());
        assertEquals(NOW, token.getRevokedAt());
        assertNull(user.getEmailVerifiedAt());
        verify(userRepository, never()).save(user);
    }

    @Test
    void verifiedEmailChangeUpdatesLoginIdentityAndRevokesSessions() {
        User user = user(4L, "old@example.com", true);
        user.setUsername("old@example.com");
        when(userRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("new@example.com")).thenReturn(false);
        when(mailer.send(anyString(), anyString(), anyString(), any(), anyLong())).thenReturn(true);

        EmailVerificationService.DispatchResult dispatch = service.requestEmailChange(
                4L, " NEW@example.com ", "127.0.0.1");

        assertTrue(dispatch.emailSent());
        assertEquals("new@example.com", user.getPendingEmail());
        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).saveAndFlush(tokenCaptor.capture());
        EmailVerificationToken token = tokenCaptor.getValue();
        when(tokenRepository.findByTokenHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token));

        EmailVerificationService.ConfirmationResult result = service.confirm(
                "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc");

        assertTrue(result.emailChanged());
        assertEquals("new@example.com", user.getEmail());
        assertEquals("new@example.com", user.getUsername());
        assertNull(user.getPendingEmail());
        assertNotNull(user.getEmailVerifiedAt());
        verify(authSessionRevocationService).revokeUserSession(4L, "EMAIL_CHANGE");
    }

    @Test
    void emailChangeRejectsIdentityOwnedByAnotherAccount() {
        User user = user(5L, "old@example.com", true);
        User existing = user(6L, "taken@example.com", true);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("taken@example.com")).thenReturn(Optional.of(existing));

        EmailVerificationException exception = assertThrows(
                EmailVerificationException.class,
                () -> service.requestEmailChange(5L, "taken@example.com", "127.0.0.1"));

        assertEquals("EMAIL_IDENTITY_CONFLICT", exception.getCode());
        assertNull(user.getPendingEmail());
        verify(tokenRepository, never()).saveAndFlush(any());
    }

    @Test
    void emailChangeRejectsAddressAlreadyPendingForAnotherAccount() {
        User user = user(8L, "old@example.com", true);
        User existing = user(9L, "other@example.com", true);
        existing.setPendingEmail("pending@example.com");
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("pending@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPendingEmailIgnoreCase("pending@example.com")).thenReturn(Optional.of(existing));

        EmailVerificationException exception = assertThrows(
                EmailVerificationException.class,
                () -> service.requestEmailChange(8L, "pending@example.com", "127.0.0.1"));

        assertEquals("EMAIL_IDENTITY_CONFLICT", exception.getCode());
        assertNull(user.getPendingEmail());
    }

    @Test
    void resendRateLimitDoesNotCreateAnotherToken() {
        User user = user(7L, "guest@example.com", false);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(tokenRepository.countByUser_IdAndRequestedAtAfter(anyLong(), any())).thenReturn(3L);

        EmailVerificationService.DispatchResult result = service.resend(7L, "127.0.0.1");

        assertFalse(result.emailSent());
        verify(tokenRepository, never()).saveAndFlush(any());
        verify(mailer, never()).send(anyString(), anyString(), anyString(), any(), anyLong());
    }

    private User user(Long id, String email, boolean verified) {
        User user = new User();
        user.setId(id);
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName("Guest");
        user.setEmailVerifiedAt(verified ? NOW.minusSeconds(60) : null);
        return user;
    }

    private EmailVerificationToken token(
            User user,
            EmailVerificationPurpose purpose,
            String targetEmail,
            Instant expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTargetEmail(targetEmail);
        token.setExpiresAt(expiresAt);
        token.setRequestedAt(NOW.minusSeconds(30));
        token.setRequestIp("127.0.0.1");
        token.setTokenHash(PasswordResetService.hashToken("raw-token"));
        return token;
    }
}

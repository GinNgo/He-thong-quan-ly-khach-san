package com.hotel.services;

import com.hotel.entities.AuthLoginAttempt;
import com.hotel.entities.User;
import com.hotel.observability.OperationalMetrics;
import com.hotel.repositories.AuthLoginAttemptRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.LoginTemporarilyBlockedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginSecurityServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T03:00:00Z");

    @Test
    void fifthKnownAccountFailureLocksAndUnlocksAfterConfiguredDuration() {
        AuthLoginAttemptRepository attempts = mock(AuthLoginAttemptRepository.class);
        UserRepository users = mock(UserRepository.class);
        OperationalAuditService audit = mock(OperationalAuditService.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        User user = activeUser();
        when(users.findByUsername("owner@example.com")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(attempts.countByIpFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(0L);

        LoginSecurityService service = service(attempts, users, audit, metrics, NOW);
        LoginSecurityService.LoginGuard guard = service.preAuthenticate(
                " OWNER@EXAMPLE.COM ", "203.0.113.10", "corr-lock");

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(service.recordFailure(guard).blocked()).isFalse();
        }
        LoginSecurityService.BlockDecision decision = service.recordFailure(guard);

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.retryAfterSeconds()).isEqualTo(900);
        assertThat(user.getFailedLoginCount()).isEqualTo(5);
        assertThat(user.getLoginLockedUntil()).isEqualTo(NOW.plusSeconds(900));

        LoginSecurityService afterLock = service(attempts, users, audit, metrics, NOW.plusSeconds(901));
        afterLock.preAuthenticate("owner@example.com", "203.0.113.10", "corr-unlock");

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLoginLockedUntil()).isNull();
        assertThat(user.getFailedLoginWindowStartedAt()).isNull();
    }

    @Test
    void ipThresholdBlocksBeforePasswordVerification() {
        AuthLoginAttemptRepository attempts = mock(AuthLoginAttemptRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("unknown@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(attempts.countByAccountFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(0L);
        when(attempts.countByIpFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(20L);

        LoginSecurityService service = service(attempts, users,
                mock(OperationalAuditService.class), mock(OperationalMetrics.class), NOW);

        assertThatThrownBy(() -> service.preAuthenticate(
                "unknown@example.com", "198.51.100.22", "corr-ip"))
                .isInstanceOf(LoginTemporarilyBlockedException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(900L);
    }

    @Test
    void persistedAuditUsesHmacFingerprintsInsteadOfRawIdentifierOrAddress() {
        AuthLoginAttemptRepository attempts = mock(AuthLoginAttemptRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("person@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(attempts.countByAccountFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(0L, 1L);
        when(attempts.countByIpFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(0L, 1L);

        LoginSecurityService service = service(attempts, users,
                mock(OperationalAuditService.class), mock(OperationalMetrics.class), NOW);
        LoginSecurityService.LoginGuard guard = service.preAuthenticate(
                "Person@Example.com", "192.0.2.77", "corr-private");
        service.recordFailure(guard);

        ArgumentCaptor<AuthLoginAttempt> captor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
        verify(attempts).save(captor.capture());
        AuthLoginAttempt stored = captor.getValue();
        assertThat(stored.getAccountFingerprint()).hasSize(64).doesNotContain("person@example.com");
        assertThat(stored.getIpFingerprint()).hasSize(64).doesNotContain("192.0.2.77");
        assertThat(stored.getReasonCode()).isEqualTo("BAD_CREDENTIALS");
        assertThat(stored.getCorrelationId()).isEqualTo("corr-private");
    }

    private LoginSecurityService service(
            AuthLoginAttemptRepository attempts,
            UserRepository users,
            OperationalAuditService audit,
            OperationalMetrics metrics,
            Instant now) {
        return new LoginSecurityService(
                attempts, users, audit, metrics, "unit-test-login-audit-secret",
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15), Duration.ofMinutes(15), 5, 20);
    }

    private User activeUser() {
        User user = new User();
        user.setId(42L);
        user.setUsername("owner@example.com");
        user.setEmail("owner@example.com");
        user.setStatus("ACTIVE");
        user.setFailedLoginCount(0);
        return user;
    }
}

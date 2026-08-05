package com.hotel.services;

import com.hotel.entities.AuthLoginAttempt;
import com.hotel.entities.User;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.observability.OperationalMetrics;
import com.hotel.repositories.AuthLoginAttemptRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.LoginTemporarilyBlockedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Service
public class LoginSecurityService {

    static final String FAILURE = "FAILURE";
    private static final String SUCCESS = "SUCCESS";
    private static final String BLOCKED = "BLOCKED";

    private final AuthLoginAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final OperationalAuditService auditService;
    private final OperationalMetrics metrics;
    private final byte[] auditSecret;
    private final Clock clock;
    private final Duration window;
    private final Duration lockDuration;
    private final int maxAccountFailures;
    private final int maxIpFailures;

    @Autowired
    public LoginSecurityService(
            AuthLoginAttemptRepository attemptRepository,
            UserRepository userRepository,
            OperationalAuditService auditService,
            OperationalMetrics metrics,
            @Value("${app.auth.login.audit-secret}") String auditSecret,
            @Value("${app.auth.login.window-minutes:15}") long windowMinutes,
            @Value("${app.auth.login.lock-minutes:15}") long lockMinutes,
            @Value("${app.auth.login.max-account-failures:5}") int maxAccountFailures,
            @Value("${app.auth.login.max-ip-failures:20}") int maxIpFailures) {
        this(attemptRepository, userRepository, auditService, metrics, auditSecret, Clock.systemUTC(),
                Duration.ofMinutes(windowMinutes), Duration.ofMinutes(lockMinutes),
                maxAccountFailures, maxIpFailures);
    }

    LoginSecurityService(
            AuthLoginAttemptRepository attemptRepository,
            UserRepository userRepository,
            OperationalAuditService auditService,
            OperationalMetrics metrics,
            String auditSecret,
            Clock clock,
            Duration window,
            Duration lockDuration,
            int maxAccountFailures,
            int maxIpFailures) {
        if (auditSecret == null || auditSecret.isBlank()) {
            throw new IllegalStateException("Login audit secret is required.");
        }
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.metrics = metrics;
        this.auditSecret = auditSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.window = window;
        this.lockDuration = lockDuration;
        this.maxAccountFailures = maxAccountFailures;
        this.maxIpFailures = maxIpFailures;
    }

    public LoginGuard preAuthenticate(String identifier, String remoteAddress, String correlationId) {
        Instant now = clock.instant();
        String normalizedIdentifier = normalize(identifier, "blank-account");
        String normalizedAddress = normalize(remoteAddress, "unknown-address");
        String accountFingerprint = fingerprint("account", normalizedIdentifier);
        String ipFingerprint = fingerprint("ip", normalizedAddress);
        String safeCorrelation = CorrelationIdSupport.normalize(correlationId);
        User user = findUser(normalizedIdentifier).orElse(null);

        if (user != null && user.getLoginLockedUntil() != null) {
            if (user.getLoginLockedUntil().isAfter(now)) {
                blockAndThrow(user.getId(), accountFingerprint, ipFingerprint,
                        "ACCOUNT_LOCKED", safeCorrelation, now,
                        Duration.between(now, user.getLoginLockedUntil()).toSeconds());
            }
            clearAccountFailures(user);
            userRepository.save(user);
            appendOperationalAudit(user.getId(), accountFingerprint, ipFingerprint,
                    "LOGIN_LOCK_EXPIRED", "TIME_BASED_UNLOCK", safeCorrelation, null);
        }

        Instant windowStart = now.minus(window);
        if (user == null && attemptRepository
                .countByAccountFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(
                        accountFingerprint, FAILURE, windowStart) >= maxAccountFailures) {
            blockAndThrow(null, accountFingerprint, ipFingerprint, "ACCOUNT_LIMIT",
                    safeCorrelation, now, lockDuration.toSeconds());
        }
        if (attemptRepository.countByIpFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(
                ipFingerprint, FAILURE, windowStart) >= maxIpFailures) {
            blockAndThrow(user == null ? null : user.getId(), accountFingerprint, ipFingerprint,
                    "IP_LIMIT", safeCorrelation, now, lockDuration.toSeconds());
        }
        return new LoginGuard(user == null ? null : user.getId(), accountFingerprint, ipFingerprint, safeCorrelation);
    }

    @Transactional
    public BlockDecision recordFailure(LoginGuard guard) {
        Instant now = clock.instant();
        User user = guard.userId() == null ? null : userRepository.findByIdForUpdate(guard.userId()).orElse(null);
        int accountFailures = 0;
        Instant lockedUntil = null;
        if (user != null) {
            if (user.getFailedLoginWindowStartedAt() == null
                    || user.getFailedLoginWindowStartedAt().isBefore(now.minus(window))) {
                user.setFailedLoginWindowStartedAt(now);
                user.setFailedLoginCount(0);
            }
            accountFailures = user.getFailedLoginCount() == null ? 1 : user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(accountFailures);
            if (accountFailures >= maxAccountFailures) {
                lockedUntil = now.plus(lockDuration);
                user.setLoginLockedUntil(lockedUntil);
            }
            userRepository.save(user);
        }

        persistAttempt(guard, FAILURE, "BAD_CREDENTIALS", now);
        long unknownAccountFailures = user == null ? attemptRepository
                .countByAccountFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(
                        guard.accountFingerprint(), FAILURE, now.minus(window)) : accountFailures;
        long ipFailures = attemptRepository.countByIpFingerprintAndOutcomeAndOccurredAtGreaterThanEqual(
                guard.ipFingerprint(), FAILURE, now.minus(window));

        boolean accountBlocked = unknownAccountFailures >= maxAccountFailures;
        boolean ipBlocked = ipFailures >= maxIpFailures;
        String reason = accountBlocked && ipBlocked ? "ACCOUNT_AND_IP_LIMIT"
                : accountBlocked ? "ACCOUNT_LIMIT" : ipBlocked ? "IP_LIMIT" : "BAD_CREDENTIALS";
        appendOperationalAudit(guard.userId(), guard.accountFingerprint(), guard.ipFingerprint(),
                accountBlocked || ipBlocked ? "LOGIN_BLOCKED" : "LOGIN_FAILURE",
                reason, guard.correlationId(), lockedUntil);
        metrics.recordAuthLogin(accountBlocked || ipBlocked ? "blocked" : "failure", reason);
        return accountBlocked || ipBlocked
                ? new BlockDecision(true, lockDuration.toSeconds())
                : BlockDecision.allowed();
    }

    @Transactional
    public void recordSuccess(LoginGuard guard, Long authenticatedUserId) {
        Instant now = clock.instant();
        userRepository.findByIdForUpdate(authenticatedUserId).ifPresent(user -> {
            clearAccountFailures(user);
            user.setLastLoginAt(now);
            userRepository.save(user);
        });
        persistAttempt(new LoginGuard(authenticatedUserId, guard.accountFingerprint(),
                guard.ipFingerprint(), guard.correlationId()), SUCCESS, "AUTHENTICATED", now);
        appendOperationalAudit(authenticatedUserId, guard.accountFingerprint(), guard.ipFingerprint(),
                "LOGIN_SUCCESS", "AUTHENTICATED", guard.correlationId(), null);
        metrics.recordAuthLogin("success", "authenticated");
    }

    private void blockAndThrow(Long userId, String accountFingerprint, String ipFingerprint,
                               String reason, String correlationId, Instant now, long retryAfterSeconds) {
        LoginGuard guard = new LoginGuard(userId, accountFingerprint, ipFingerprint, correlationId);
        persistAttempt(guard, BLOCKED, reason, now);
        appendOperationalAudit(userId, accountFingerprint, ipFingerprint,
                "LOGIN_BLOCKED", reason, correlationId, null);
        metrics.recordAuthLogin("blocked", reason);
        throw new LoginTemporarilyBlockedException(retryAfterSeconds);
    }

    private void persistAttempt(LoginGuard guard, String outcome, String reason, Instant occurredAt) {
        attemptRepository.save(new AuthLoginAttempt(
                guard.userId(), guard.accountFingerprint(), guard.ipFingerprint(),
                outcome, reason, guard.correlationId(), occurredAt));
    }

    private void appendOperationalAudit(Long userId, String accountFingerprint, String ipFingerprint,
                                        String eventType, String reason, String correlationId,
                                        Instant lockedUntil) {
        auditService.append(new OperationalAuditService.AuditCommand(
                "SYSTEM", null, "AUTH", eventType, "LOGIN_ACCOUNT", accountFingerprint,
                "SYSTEM", null, reason, null,
                Map.of("userId", userId == null ? "unknown" : userId,
                        "accountFingerprint", accountFingerprint,
                        "ipFingerprint", ipFingerprint,
                        "lockedUntil", lockedUntil == null ? "none" : lockedUntil.toString()),
                correlationId));
    }

    private java.util.Optional<User> findUser(String normalizedIdentifier) {
        return userRepository.findByUsername(normalizedIdentifier)
                .or(() -> userRepository.findByEmail(normalizedIdentifier));
    }

    private void clearAccountFailures(User user) {
        user.setFailedLoginCount(0);
        user.setFailedLoginWindowStartedAt(null);
        user.setLoginLockedUntil(null);
    }

    private String fingerprint(String namespace, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(auditSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((namespace + ":" + value)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Login fingerprinting is unavailable.", exception);
        }
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip().toLowerCase(Locale.ROOT);
    }

    public record LoginGuard(Long userId, String accountFingerprint, String ipFingerprint, String correlationId) { }

    public record BlockDecision(boolean blocked, long retryAfterSeconds) {
        static BlockDecision allowed() { return new BlockDecision(false, 0); }
    }
}

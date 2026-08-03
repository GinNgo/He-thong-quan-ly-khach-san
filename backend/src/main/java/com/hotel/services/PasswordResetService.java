package com.hotel.services;

import com.hotel.entities.PasswordResetToken;
import com.hotel.entities.User;
import com.hotel.repositories.PasswordResetTokenRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.PasswordPolicy;
import com.hotel.security.PasswordResetException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/** Enumeration-safe, one-time password reset workflow. */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final String GENERIC_RESPONSE =
            "If the account exists, a password reset link will be sent shortly.";

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthSessionRevocationService authSessionRevocationService;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final long tokenTtlMinutes;
    private final long rateWindowMinutes;
    private final long maxIpRequests;
    private final long maxEmailRequests;
    private final String resetUrl;

    @Autowired
    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuthSessionRevocationService authSessionRevocationService,
            @Value("${app.auth.password-reset.ttl-minutes:30}") long tokenTtlMinutes,
            @Value("${app.auth.password-reset.rate-window-minutes:15}") long rateWindowMinutes,
            @Value("${app.auth.password-reset.max-ip-requests:5}") long maxIpRequests,
            @Value("${app.auth.password-reset.max-email-requests:3}") long maxEmailRequests,
            @Value("${app.mail.password-reset-url:http://localhost:4200/reset-password}") String resetUrl) {
        this(tokenRepository, userRepository, passwordEncoder, emailService,
                authSessionRevocationService, Clock.systemUTC(), new SecureRandom(),
                tokenTtlMinutes, rateWindowMinutes, maxIpRequests, maxEmailRequests, resetUrl);
    }

    PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuthSessionRevocationService authSessionRevocationService,
            Clock clock,
            SecureRandom secureRandom,
            long tokenTtlMinutes,
            long rateWindowMinutes,
            long maxIpRequests,
            long maxEmailRequests,
            String resetUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.authSessionRevocationService = authSessionRevocationService;
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.tokenTtlMinutes = Math.max(5, tokenTtlMinutes);
        this.rateWindowMinutes = Math.max(1, rateWindowMinutes);
        this.maxIpRequests = Math.max(1, maxIpRequests);
        this.maxEmailRequests = Math.max(1, maxEmailRequests);
        this.resetUrl = resetUrl == null || resetUrl.isBlank()
                ? "http://localhost:4200/reset-password"
                : resetUrl.strip();
    }

    public String genericResponseMessage() {
        return GENERIC_RESPONSE;
    }

    @Transactional
    public void requestReset(String email, String requestIp) {
        String normalizedEmail = normalizeEmail(email);
        String emailFingerprint = hashToken(normalizedEmail);
        String safeIp = normalizeIp(requestIp);
        Instant now = clock.instant();
        Instant windowStart = now.minus(Duration.ofMinutes(rateWindowMinutes));

        if (tokenRepository.countByRequestIpAndRequestedAtAfter(safeIp, windowStart) >= maxIpRequests
                || tokenRepository.countByEmailFingerprintAndRequestedAtAfter(emailFingerprint, windowStart)
                >= maxEmailRequests) {
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        tokenRepository.revokeActiveForFingerprint(emailFingerprint, now);

        String rawToken = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setEmailFingerprint(emailFingerprint);
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setRequestedAt(now);
        resetToken.setExpiresAt(now.plus(Duration.ofMinutes(tokenTtlMinutes)));
        resetToken.setRequestIp(safeIp);
        tokenRepository.saveAndFlush(resetToken);

        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFullName(),
                    buildResetUrl(rawToken),
                    tokenTtlMinutes);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw PasswordResetException.invalidToken();
        }
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository
                .findByTokenHashForUpdate(hashToken(rawToken.strip()))
                .orElseThrow(PasswordResetException::invalidToken);

        if (resetToken.getUsedAt() != null || resetToken.getRevokedAt() != null) {
            throw PasswordResetException.invalidToken();
        }
        if (!now.isBefore(resetToken.getExpiresAt())) {
            resetToken.setRevokedAt(now);
            tokenRepository.save(resetToken);
            throw PasswordResetException.expiredToken();
        }
        User user = resetToken.getUser();
        if (user == null) {
            throw PasswordResetException.invalidToken();
        }
        PasswordPolicy.requireValid(newPassword);

        resetToken.setUsedAt(now);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tokenRepository.save(resetToken);
        userRepository.save(user);
        authSessionRevocationService.revokeUserSession(user.getId(), "PASSWORD_RESET");
    }

    public static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildResetUrl(String rawToken) {
        String separator = resetUrl.contains("?") ? "&" : "?";
        return resetUrl + separator + "token=" + UriUtils.encodeQueryParam(rawToken, StandardCharsets.UTF_8);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String requestIp) {
        if (requestIp == null || requestIp.isBlank()) return "unknown";
        String value = requestIp.strip();
        return value.length() <= 64 ? value : hashToken(value).substring(0, 64);
    }
}

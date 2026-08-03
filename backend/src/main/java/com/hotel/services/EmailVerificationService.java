package com.hotel.services;

import com.hotel.entities.EmailVerificationPurpose;
import com.hotel.entities.EmailVerificationToken;
import com.hotel.entities.User;
import com.hotel.repositories.EmailVerificationTokenRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.EmailVerificationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailVerificationMailer mailer;
    private final AuthSessionRevocationService authSessionRevocationService;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final long tokenTtlMinutes;
    private final long rateWindowMinutes;
    private final long maxIpRequests;
    private final long maxUserRequests;
    private final String verificationUrl;

    @Autowired
    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailVerificationMailer mailer,
            AuthSessionRevocationService authSessionRevocationService,
            @Value("${app.auth.email-verification.ttl-minutes:60}") long tokenTtlMinutes,
            @Value("${app.auth.email-verification.rate-window-minutes:15}") long rateWindowMinutes,
            @Value("${app.auth.email-verification.max-ip-requests:8}") long maxIpRequests,
            @Value("${app.auth.email-verification.max-user-requests:3}") long maxUserRequests,
            @Value("${app.mail.email-verification-url:http://localhost:4200/verify-email}") String verificationUrl) {
        this(tokenRepository, userRepository, mailer, authSessionRevocationService,
                Clock.systemUTC(), new SecureRandom(), tokenTtlMinutes, rateWindowMinutes,
                maxIpRequests, maxUserRequests, verificationUrl);
    }

    EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailVerificationMailer mailer,
            AuthSessionRevocationService authSessionRevocationService,
            Clock clock,
            SecureRandom secureRandom,
            long tokenTtlMinutes,
            long rateWindowMinutes,
            long maxIpRequests,
            long maxUserRequests,
            String verificationUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailer = mailer;
        this.authSessionRevocationService = authSessionRevocationService;
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.tokenTtlMinutes = Math.max(5, tokenTtlMinutes);
        this.rateWindowMinutes = Math.max(1, rateWindowMinutes);
        this.maxIpRequests = Math.max(1, maxIpRequests);
        this.maxUserRequests = Math.max(1, maxUserRequests);
        this.verificationUrl = verificationUrl == null || verificationUrl.isBlank()
                ? "http://localhost:4200/verify-email"
                : verificationUrl.strip();
    }

    @Transactional
    public boolean requestInitialVerification(User user) {
        if (user == null || user.getId() == null || user.getEmailVerifiedAt() != null) return false;
        return issue(user, user.getEmail(), EmailVerificationPurpose.INITIAL_VERIFICATION, "registration").emailSent();
    }

    @Transactional
    public DispatchResult resend(Long userId, String requestIp) {
        User user = requireUserForUpdate(userId);
        if (user.getPendingEmail() != null && !user.getPendingEmail().isBlank()) {
            return issue(user, user.getPendingEmail(), EmailVerificationPurpose.EMAIL_CHANGE, requestIp);
        }
        if (user.getEmailVerifiedAt() != null) {
            return new DispatchResult(false, true, null);
        }
        return issue(user, user.getEmail(), EmailVerificationPurpose.INITIAL_VERIFICATION, requestIp);
    }

    @Transactional
    public DispatchResult requestEmailChange(Long userId, String newEmail, String requestIp) {
        User user = requireUserForUpdate(userId);
        String normalizedEmail = normalizeEmail(newEmail);

        if (normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            if (user.getEmailVerifiedAt() != null) return new DispatchResult(false, true, null);
            return issue(user, user.getEmail(), EmailVerificationPurpose.INITIAL_VERIFICATION, requestIp);
        }
        requireAvailableIdentity(user, normalizedEmail);
        if (rateLimited(user.getId(), normalizeIp(requestIp))) {
            return new DispatchResult(false, false, user.getPendingEmail());
        }

        user.setPendingEmail(normalizedEmail);
        userRepository.save(user);
        return issueWithoutRateCheck(user, normalizedEmail, EmailVerificationPurpose.EMAIL_CHANGE, requestIp);
    }

    @Transactional
    public ConfirmationResult confirm(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw EmailVerificationException.invalidToken();
        }

        Instant now = clock.instant();
        EmailVerificationToken token = tokenRepository
                .findByTokenHashForUpdate(PasswordResetService.hashToken(rawToken.strip()))
                .orElseThrow(EmailVerificationException::invalidToken);
        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            throw EmailVerificationException.invalidToken();
        }
        if (!now.isBefore(token.getExpiresAt())) {
            token.setRevokedAt(now);
            tokenRepository.save(token);
            throw EmailVerificationException.expiredToken();
        }

        User user = requireUserForUpdate(token.getUser().getId());
        boolean emailChanged = token.getPurpose() == EmailVerificationPurpose.EMAIL_CHANGE;
        if (emailChanged) {
            if (user.getPendingEmail() == null
                    || !user.getPendingEmail().equalsIgnoreCase(token.getTargetEmail())) {
                throw EmailVerificationException.invalidToken();
            }
            requireAvailableIdentity(user, token.getTargetEmail());
            String oldEmail = user.getEmail();
            user.setEmail(token.getTargetEmail());
            if (user.getUsername() != null && user.getUsername().equalsIgnoreCase(oldEmail)) {
                user.setUsername(token.getTargetEmail());
            }
            user.setPendingEmail(null);
            authSessionRevocationService.revokeUserSession(user.getId(), "EMAIL_CHANGE");
        } else if (!user.getEmail().equalsIgnoreCase(token.getTargetEmail())) {
            throw EmailVerificationException.invalidToken();
        }

        user.setEmailVerifiedAt(now);
        token.setUsedAt(now);
        userRepository.save(user);
        tokenRepository.saveAndFlush(token);
        tokenRepository.revokeActiveForUserAndPurpose(user.getId(), token.getPurpose(), now);
        return new ConfirmationResult(emailChanged, user.getEmail());
    }

    private DispatchResult issue(
            User user,
            String targetEmail,
            EmailVerificationPurpose purpose,
            String requestIp) {
        String safeIp = normalizeIp(requestIp);
        if (rateLimited(user.getId(), safeIp)) {
            return new DispatchResult(false, false, user.getPendingEmail());
        }
        return issueWithoutRateCheck(user, targetEmail, purpose, safeIp);
    }

    private DispatchResult issueWithoutRateCheck(
            User user,
            String targetEmail,
            EmailVerificationPurpose purpose,
            String requestIp) {
        Instant now = clock.instant();
        tokenRepository.revokeActiveForUserAndPurpose(user.getId(), purpose, now);

        String rawToken = generateToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTargetEmail(targetEmail);
        token.setTokenHash(PasswordResetService.hashToken(rawToken));
        token.setRequestedAt(now);
        token.setExpiresAt(now.plus(Duration.ofMinutes(tokenTtlMinutes)));
        token.setRequestIp(normalizeIp(requestIp));
        tokenRepository.saveAndFlush(token);

        boolean sent = mailer.send(
                targetEmail, user.getFullName(), buildVerificationUrl(rawToken), purpose, tokenTtlMinutes);
        return new DispatchResult(sent, false, user.getPendingEmail());
    }

    private boolean rateLimited(Long userId, String requestIp) {
        Instant windowStart = clock.instant().minus(Duration.ofMinutes(rateWindowMinutes));
        return tokenRepository.countByUser_IdAndRequestedAtAfter(userId, windowStart) >= maxUserRequests
                || tokenRepository.countByRequestIpAndRequestedAtAfter(requestIp, windowStart) >= maxIpRequests;
    }

    private void requireAvailableIdentity(User user, String normalizedEmail) {
        boolean emailOwnedByOther = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .isPresent();
        boolean emailPendingForOther = userRepository.findByPendingEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .isPresent();
        boolean usernameOwnedByOther = userRepository.existsByUsernameIgnoreCase(normalizedEmail)
                && (user.getUsername() == null || !user.getUsername().equalsIgnoreCase(normalizedEmail));
        if (emailOwnedByOther || emailPendingForOther || usernameOwnedByOther) {
            throw EmailVerificationException.identityConflict();
        }
    }

    private User requireUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(EmailVerificationException::invalidToken);
    }

    private String buildVerificationUrl(String rawToken) {
        String separator = verificationUrl.contains("?") ? "&" : "?";
        return verificationUrl + separator + "token="
                + UriUtils.encodeQueryParam(rawToken, StandardCharsets.UTF_8);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Email is required.");
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String safe = value.strip();
        return safe.length() <= 64 ? safe : PasswordResetService.hashToken(safe).substring(0, 64);
    }

    public record DispatchResult(boolean emailSent, boolean alreadyVerified, String pendingEmail) { }
    public record ConfirmationResult(boolean emailChanged, String email) { }
}

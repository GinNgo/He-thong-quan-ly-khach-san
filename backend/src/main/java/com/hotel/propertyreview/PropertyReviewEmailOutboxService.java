package com.hotel.propertyreview;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyReviewEmailOutboxService {

    private static final Set<PropertyReviewEmailStatus> DUE_STATUSES = Set.of(
            PropertyReviewEmailStatus.PENDING,
            PropertyReviewEmailStatus.FAILED);

    private final PropertyReviewEmailOutboxRepository outboxRepository;
    private final PropertyReviewEmailDeliveryAttemptRepository attemptRepository;
    private final Clock clock;
    private final int maxAttempts;
    private final int batchSize;
    private final long claimTimeoutSeconds;
    private final long baseBackoffSeconds;
    private final long maxBackoffSeconds;

    @Autowired
    public PropertyReviewEmailOutboxService(
            PropertyReviewEmailOutboxRepository outboxRepository,
            PropertyReviewEmailDeliveryAttemptRepository attemptRepository,
            @Value("${app.mail.property-review.max-attempts:5}") int maxAttempts,
            @Value("${app.mail.property-review.batch-size:25}") int batchSize,
            @Value("${app.mail.property-review.claim-timeout-seconds:300}") long claimTimeoutSeconds,
            @Value("${app.mail.property-review.base-backoff-seconds:30}") long baseBackoffSeconds,
            @Value("${app.mail.property-review.max-backoff-seconds:3600}") long maxBackoffSeconds) {
        this(outboxRepository, attemptRepository, Clock.systemUTC(), maxAttempts, batchSize,
                claimTimeoutSeconds, baseBackoffSeconds, maxBackoffSeconds);
    }

    PropertyReviewEmailOutboxService(
            PropertyReviewEmailOutboxRepository outboxRepository,
            PropertyReviewEmailDeliveryAttemptRepository attemptRepository,
            Clock clock,
            int maxAttempts,
            int batchSize,
            long claimTimeoutSeconds,
            long baseBackoffSeconds,
            long maxBackoffSeconds) {
        this.outboxRepository = outboxRepository;
        this.attemptRepository = attemptRepository;
        this.clock = clock;
        this.maxAttempts = Math.min(Math.max(maxAttempts, 1), 20);
        this.batchSize = Math.min(Math.max(batchSize, 1), 100);
        this.claimTimeoutSeconds = Math.max(claimTimeoutSeconds, 30);
        this.baseBackoffSeconds = Math.max(baseBackoffSeconds, 1);
        this.maxBackoffSeconds = Math.max(this.baseBackoffSeconds, maxBackoffSeconds);
    }

    @Transactional
    public EnqueueResult enqueue(
            Long auditEventId,
            Long hotelId,
            Long recipientUserId,
            String recipientEmail,
            String subject,
            String bodyText,
            LocalDateTime createdAt) {
        requireId(auditEventId, "Audit event");
        requireId(hotelId, "Property");
        requireId(recipientUserId, "Email recipient user");
        String email = normalizeEmail(recipientEmail);
        String safeSubject = requireText(subject, "Email subject", 500);
        String safeBody = requireText(bodyText, "Email body", 10_000);

        PropertyReviewEmailOutbox existing = outboxRepository
                .findByAuditEventIdAndRecipientUserId(auditEventId, recipientUserId)
                .orElse(null);
        if (existing != null) {
            return new EnqueueResult(existing.getId(), existing.getStatus(), true);
        }

        LocalDateTime now = createdAt == null ? now() : createdAt;
        PropertyReviewEmailOutbox item = new PropertyReviewEmailOutbox(
                        auditEventId,
                        hotelId,
                        recipientUserId,
                        email,
                        safeSubject,
                        safeBody,
                        maxAttempts,
                        now);
        if (email == null) {
            item.markDeadLetter("RECIPIENT_INVALID", now);
        }
        PropertyReviewEmailOutbox saved = outboxRepository.saveAndFlush(item);
        if (email == null) {
            attemptRepository.save(new PropertyReviewEmailDeliveryAttempt(
                    saved,
                    1,
                    PropertyReviewEmailOutcome.FAILED,
                    "RECIPIENT_INVALID",
                    0,
                    now));
        }
        return new EnqueueResult(saved.getId(), saved.getStatus(), false);
    }

    @Transactional
    public List<DispatchClaim> claimDue() {
        LocalDateTime now = now();
        LocalDateTime staleBefore = now.minusSeconds(claimTimeoutSeconds);
        List<PropertyReviewEmailOutbox> due = outboxRepository.findDueForUpdate(
                now,
                staleBefore,
                DUE_STATUSES,
                PropertyReviewEmailStatus.PROCESSING,
                PageRequest.of(0, batchSize));

        return due.stream()
                .map(item -> claim(item, now))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private DispatchClaim claim(PropertyReviewEmailOutbox item, LocalDateTime now) {
        if (item.getStatus() == PropertyReviewEmailStatus.PROCESSING) {
            recordFailure(item, "CLAIM_TIMEOUT", elapsedMillis(item.getClaimedAt(), now), now);
            return null;
        }
        String token = UUID.randomUUID().toString();
        item.claim(token, now);
        outboxRepository.save(item);
        return new DispatchClaim(
                item.getId(), token, item.getRecipientEmail(), item.getSubject(), item.getBodyText());
    }

    @Transactional
    public void complete(DispatchClaim claim, boolean delivered, long durationMs) {
        if (claim == null || claim.outboxId() == null || claim.claimToken() == null) {
            return;
        }
        PropertyReviewEmailOutbox item = outboxRepository.findByIdForUpdate(claim.outboxId())
                .orElse(null);
        if (item == null
                || item.getStatus() != PropertyReviewEmailStatus.PROCESSING
                || !claim.claimToken().equals(item.getClaimToken())) {
            return;
        }
        LocalDateTime now = now();
        if (delivered) {
            int attemptNumber = item.getAttemptCount() + 1;
            item.markSent(now);
            outboxRepository.save(item);
            attemptRepository.save(new PropertyReviewEmailDeliveryAttempt(
                    item,
                    attemptNumber,
                    PropertyReviewEmailOutcome.SENT,
                    null,
                    Math.max(durationMs, 0),
                    now));
            return;
        }
        recordFailure(item, "DELIVERY_FAILED", Math.max(durationMs, 0), now);
    }

    private void recordFailure(
            PropertyReviewEmailOutbox item,
            String errorCode,
            long durationMs,
            LocalDateTime now) {
        int attemptNumber = item.getAttemptCount() + 1;
        LocalDateTime nextAttempt = now.plusSeconds(backoffSeconds(attemptNumber));
        item.markFailed(errorCode, nextAttempt, now);
        outboxRepository.save(item);
        attemptRepository.save(new PropertyReviewEmailDeliveryAttempt(
                item,
                attemptNumber,
                PropertyReviewEmailOutcome.FAILED,
                errorCode,
                Math.max(durationMs, 0),
                now));
    }

    private long backoffSeconds(int attemptNumber) {
        long multiplier = 1L << Math.min(Math.max(attemptNumber - 1, 0), 20);
        return Math.min(maxBackoffSeconds, baseBackoffSeconds * multiplier);
    }

    private long elapsedMillis(LocalDateTime startedAt, LocalDateTime completedAt) {
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
            return 0;
        }
        return Duration.between(startedAt, completedAt).toMillis();
    }

    private void requireId(Long value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " id is required.");
        }
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 320
                || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return null;
        }
        return normalized;
    }

    private String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long.");
        }
        return normalized;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record EnqueueResult(
            Long outboxId,
            PropertyReviewEmailStatus status,
            boolean replayed) {
    }

    public record DispatchClaim(
            Long outboxId,
            String claimToken,
            String recipientEmail,
            String subject,
            String bodyText) {
    }
}

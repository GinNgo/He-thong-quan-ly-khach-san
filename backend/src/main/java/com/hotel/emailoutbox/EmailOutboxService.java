package com.hotel.emailoutbox;

import com.hotel.services.OperationalAuditService;
import com.hotel.services.PropertyAccessService;
import com.hotel.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.hotel.emailoutbox.EmailOutboxDtos.DeliveryAttempt;
import static com.hotel.emailoutbox.EmailOutboxDtos.EnqueueRequest;
import static com.hotel.emailoutbox.EmailOutboxDtos.EnqueueResult;
import static com.hotel.emailoutbox.EmailOutboxDtos.Failure;

@Service
public class EmailOutboxService {

    private static final Set<EmailOutboxStatus> FAILURE_STATES = Set.of(
            EmailOutboxStatus.FAILED, EmailOutboxStatus.BOUNCED, EmailOutboxStatus.DEAD_LETTER);
    private static final Set<EmailOutboxStatus> DUE_STATES = Set.of(
            EmailOutboxStatus.PENDING, EmailOutboxStatus.FAILED);

    private final EmailOutboxRepository outboxRepository;
    private final EmailDeliveryAttemptRepository attemptRepository;
    private final EmailDeliveryPort deliveryPort;
    private final OperationalAuditService auditService;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock;
    private final long baseBackoffSeconds;
    private final long maxBackoffSeconds;
    private final int defaultMaxAttempts;

    @Autowired
    public EmailOutboxService(EmailOutboxRepository outboxRepository,
                              EmailDeliveryAttemptRepository attemptRepository,
                              EmailDeliveryPort deliveryPort,
                              OperationalAuditService auditService,
                              PropertyAccessService propertyAccessService,
                              @org.springframework.beans.factory.annotation.Value("${app.mail.outbox.base-backoff-seconds:30}") long baseBackoffSeconds,
                              @org.springframework.beans.factory.annotation.Value("${app.mail.outbox.max-backoff-seconds:3600}") long maxBackoffSeconds,
                              @org.springframework.beans.factory.annotation.Value("${app.mail.outbox.max-attempts:5}") int defaultMaxAttempts) {
        this(outboxRepository, attemptRepository, deliveryPort, auditService, propertyAccessService,
                Clock.systemUTC(), baseBackoffSeconds, maxBackoffSeconds, defaultMaxAttempts);
    }

    public EmailOutboxService(EmailOutboxRepository outboxRepository,
                              EmailDeliveryAttemptRepository attemptRepository,
                              EmailDeliveryPort deliveryPort,
                              OperationalAuditService auditService,
                              PropertyAccessService propertyAccessService,
                              Clock clock, long baseBackoffSeconds, long maxBackoffSeconds,
                              int defaultMaxAttempts) {
        this.outboxRepository = outboxRepository;
        this.attemptRepository = attemptRepository;
        this.deliveryPort = deliveryPort;
        this.auditService = auditService;
        this.propertyAccessService = propertyAccessService;
        this.clock = clock;
        this.baseBackoffSeconds = Math.max(1, baseBackoffSeconds);
        this.maxBackoffSeconds = Math.max(this.baseBackoffSeconds, maxBackoffSeconds);
        this.defaultMaxAttempts = Math.min(Math.max(defaultMaxAttempts, 1), 20);
    }

    @Transactional
    public EnqueueResult enqueue(EnqueueRequest request) {
        return enqueueInternal(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnqueueResult enqueueAfterCommit(EnqueueRequest request) {
        return enqueueInternal(request);
    }

    private EnqueueResult enqueueInternal(EnqueueRequest request) {
        Normalized normalized = normalize(request);
        EmailOutboxMessage existing = outboxRepository.findByIdempotencyKey(normalized.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(normalized.requestHash())) {
                throw new IllegalArgumentException("Email idempotency key was already used with different content.");
            }
            return new EnqueueResult(existing.getId(), existing.getStatus().name(), true);
        }

        LocalDateTime now = now();
        EmailOutboxMessage message = new EmailOutboxMessage(
                normalized.hotelId(), normalized.idempotencyKey(), normalized.requestHash(),
                normalized.templateKey(), normalized.templateVersion(), normalized.recipientEmail(),
                normalized.subject(), normalized.bodyHtml(), normalized.bodyText(),
                normalized.attachmentName(), normalized.attachmentContentType(), normalized.attachmentBytes(),
                normalized.maxAttempts(), now);
        EmailOutboxMessage saved = outboxRepository.save(message);
        appendAudit("QUEUED", saved, "Email queued for sandbox delivery.");
        return new EnqueueResult(saved.getId(), saved.getStatus().name(), false);
    }

    @Transactional(readOnly = true)
    public Page<Failure> failures(Pageable pageable) {
        Specification<EmailOutboxMessage> spec = (root, query, cb) -> root.get("status").in(FAILURE_STATES);
        if (!isSystemAdministrator()) {
            Set<Long> assigned = assignedHotelIds();
            if (assigned.isEmpty()) return Page.empty(pageable);
            spec = spec.and((root, query, cb) -> root.get("hotelId").in(assigned));
        }
        return outboxRepository.findAll(spec, pageable).map(this::toFailure);
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttempt> attempts(Long id) {
        EmailOutboxMessage message = requireAccessible(id, false);
        return attemptRepository.findByOutboxIdOrderByAttemptNumberAsc(message.getId()).stream()
                .map(item -> new DeliveryAttempt(item.getId(), item.getAttemptNumber(), item.getOutcome().name(),
                        item.getErrorCode(), item.getProviderMessageId(), item.getDurationMs(), item.getAttemptedAt()))
                .toList();
    }

    @Transactional
    public void processDueBatch() {
        LocalDateTime now = now();
        List<EmailOutboxMessage> due = outboxRepository
                .findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(DUE_STATES, now);
        for (EmailOutboxMessage candidate : due) {
            processOne(candidate.getId(), now);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(Long id, LocalDateTime observedAt) {
        EmailOutboxMessage message = outboxRepository.findForUpdate(id).orElse(null);
        if (message == null || !DUE_STATES.contains(message.getStatus())
                || message.getNextAttemptAt().isAfter(observedAt)) {
            return;
        }
        if (message.getAttemptCount() >= message.getMaxAttempts()) {
            message.markFailed("MAX_ATTEMPTS_EXCEEDED", observedAt, true, observedAt);
            outboxRepository.save(message);
            appendAudit("DEAD_LETTER", message, "Email retry limit reached.");
            return;
        }

        message.markProcessing(observedAt);
        outboxRepository.saveAndFlush(message);
        long started = System.nanoTime();
        EmailDeliveryPort.DeliveryResult result = deliveryPort.deliver(message);
        if (result == null) result = EmailDeliveryPort.DeliveryResult.failed("ADAPTER_EMPTY_RESULT");
        long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        int attemptNumber = message.getAttemptCount() + 1;
        LocalDateTime completedAt = now();
        if (result.outcome() == EmailDeliveryOutcome.SENT) {
            message.markSent(result.providerMessageId(), completedAt);
            outboxRepository.save(message);
            attemptRepository.save(new EmailDeliveryAttempt(message, attemptNumber, result.outcome(), null,
                    result.providerMessageId(), durationMs, completedAt));
            appendAudit("DELIVERED", message, "Email delivery succeeded.");
            return;
        }

        if (result.outcome() == EmailDeliveryOutcome.BOUNCED) {
            message.markBounced(safeError(result.errorCode(), "DELIVERY_BOUNCED"), completedAt);
            outboxRepository.save(message);
            attemptRepository.save(new EmailDeliveryAttempt(message, attemptNumber, result.outcome(),
                    safeError(result.errorCode(), "DELIVERY_BOUNCED"), null, durationMs, completedAt));
            appendAudit("BOUNCED", message, "Email provider reported a bounce.");
            return;
        }

        String errorCode = safeError(result.errorCode(), "DELIVERY_FAILED");
        boolean terminal = "DELIVERY_DISABLED".equals(errorCode) || attemptNumber >= message.getMaxAttempts();
        LocalDateTime nextAttempt = completedAt.plusSeconds(backoffSeconds(attemptNumber));
        message.markFailed(errorCode, nextAttempt, terminal, completedAt);
        outboxRepository.save(message);
        attemptRepository.save(new EmailDeliveryAttempt(message, attemptNumber, result.outcome(), errorCode,
                null, durationMs, completedAt));
        appendAudit(terminal ? "DEAD_LETTER" : "RETRY_SCHEDULED", message,
                terminal ? "Email delivery stopped after a terminal failure." : "Email delivery retry scheduled.");
    }

    @Transactional
    public Failure manualRetry(Long id) {
        EmailOutboxMessage message = requireAccessible(id, true);
        if (!FAILURE_STATES.contains(message.getStatus())) {
            throw new IllegalStateException("Only failed email deliveries can be retried.");
        }
        LocalDateTime now = now();
        message.manualRetry(now);
        outboxRepository.save(message);
        appendAudit("MANUAL_RETRY", message, "Operator requested a bounded email retry.");
        return toFailure(message);
    }

    @Transactional
    public Failure markBounced(Long id, String errorCode) {
        EmailOutboxMessage message = requireAccessible(id, true);
        LocalDateTime now = now();
        message.markBounced(safeError(errorCode, "DELIVERY_BOUNCED"), now);
        outboxRepository.save(message);
        appendAudit("BOUNCED", message, "Email delivery was marked as bounced.");
        return toFailure(message);
    }

    private EmailOutboxMessage requireAccessible(Long id, boolean mutation) {
        EmailOutboxMessage message = outboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Email delivery record was not found."));
        if (!isSystemAdministrator() && message.getHotelId() != null && !assignedHotelIds().contains(message.getHotelId())) {
            throw new ResourceNotFoundException("Email delivery record was not found.");
        }
        return message;
    }

    private Normalized normalize(EnqueueRequest request) {
        if (request == null) throw new IllegalArgumentException("Email request is required.");
        String key = require(request.idempotencyKey(), "Email idempotency key");
        String template = require(request.templateKey(), "Email template");
        String version = require(request.templateVersion(), "Email template version");
        String recipient = require(request.recipientEmail(), "Email recipient").trim().toLowerCase(Locale.ROOT);
        if (!recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Email recipient is invalid.");
        String subject = require(request.subject(), "Email subject");
        if ((request.bodyHtml() == null || request.bodyHtml().isBlank())
                && (request.bodyText() == null || request.bodyText().isBlank())) {
            throw new IllegalArgumentException("Email body is required.");
        }
        byte[] attachment = request.attachmentBytes() == null ? null : request.attachmentBytes().clone();
        int max = request.maxAttempts() == null ? defaultMaxAttempts : Math.min(Math.max(request.maxAttempts(), 1), 20);
        String hashInput = String.join("\u001f", Objects.toString(request.hotelId(), ""), key, template, version,
                recipient, subject, Objects.toString(request.bodyHtml(), ""), Objects.toString(request.bodyText(), ""),
                Objects.toString(request.attachmentName(), ""), Objects.toString(request.attachmentContentType(), ""),
                attachment == null ? "" : HexFormat.of().formatHex(attachment));
        return new Normalized(request.hotelId(), key, sha256(hashInput), template, version, recipient, subject,
                request.bodyHtml(), request.bodyText(), request.attachmentName(), request.attachmentContentType(), attachment, max);
    }

    private Failure toFailure(EmailOutboxMessage item) {
        return new Failure(item.getId(), item.getHotelId(), item.getIdempotencyKey(), item.getTemplateKey(),
                item.getTemplateVersion(), maskRecipient(item.getRecipientEmail()), item.getSubject(), item.getStatus().name(),
                item.getAttemptCount(), item.getMaxAttempts(), item.getManualRetryCount(), item.getLastErrorCode(),
                item.getFailedAt(), item.getNextAttemptAt(), item.getCreatedAt());
    }

    private void appendAudit(String eventType, EmailOutboxMessage message, String reason) {
        if (auditService == null) return;
        auditService.append(new OperationalAuditService.AuditCommand("SYSTEM", null, "EMAIL_OUTBOX", eventType,
                "EMAIL_OUTBOX", String.valueOf(message.getId()), null, null, reason,
                null, java.util.Map.of("status", message.getStatus().name(), "template", message.getTemplateKey()), null));
    }

    private long backoffSeconds(int attemptNumber) {
        long multiplier = 1L << Math.min(Math.max(attemptNumber - 1, 0), 20);
        return Math.min(maxBackoffSeconds, baseBackoffSeconds * multiplier);
    }

    private String maskRecipient(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at < 0 ? "" : email.substring(at));
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String safeError(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Email request hashing is unavailable.", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private boolean isSystemAdministrator() {
        return propertyAccessService == null || propertyAccessService.isSystemAdministrator();
    }

    private Set<Long> assignedHotelIds() {
        return propertyAccessService == null ? Set.of() : propertyAccessService.assignedHotelIds();
    }

    private record Normalized(Long hotelId, String idempotencyKey, String requestHash, String templateKey,
                              String templateVersion, String recipientEmail, String subject, String bodyHtml,
                              String bodyText, String attachmentName, String attachmentContentType,
                              byte[] attachmentBytes, int maxAttempts) {
    }
}

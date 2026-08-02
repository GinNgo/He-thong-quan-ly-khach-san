package com.hotel.paymentprovider.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinancialIdempotencyService {

    private final FinancialIdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    @Autowired
    public FinancialIdempotencyService(FinancialIdempotencyRepository repository, ObjectMapper objectMapper,
                                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    FinancialIdempotencyService(FinancialIdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.requiresNew = null;
    }

    public BeginResult begin(BeginCommand command) {
        validate(command);
        String key = normalize(command.idempotencyKey());
        String hash = hash(command.payload());
        if (requiresNew == null) {
            try {
                return claim(command, key, hash);
            } catch (DataIntegrityViolationException race) {
                return winner(command, key, hash);
            }
        }
        try {
            return requiresNew.execute(status -> claim(command, key, hash));
        } catch (DataIntegrityViolationException race) {
            return requiresNew.execute(status -> winner(command, key, hash));
        }
    }

    private BeginResult claim(BeginCommand command, String key, String hash) {
        var existing = repository.findByContextAndOperationAndScopeKeyAndIdempotencyKey(
                command.context(), command.operation(), command.scopeKey(), key);
        if (existing.isPresent()) {
            return existingResult(existing.get(), hash);
        }

        FinancialIdempotencyRecord candidate = new FinancialIdempotencyRecord(
                command.context(), command.operation(), command.scopeKey(), key, hash,
                command.hotelId(), command.ownerUserId(), correlation(command.correlationId()),
                LocalDateTime.now(ZoneOffset.UTC));
        return new Acquired(repository.saveAndFlush(candidate));
    }

    private BeginResult winner(BeginCommand command, String key, String hash) {
        FinancialIdempotencyRecord winner = repository.findByContextAndOperationAndScopeKeyAndIdempotencyKey(
                        command.context(), command.operation(), command.scopeKey(), key)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        return existingResult(winner, hash);
    }

    @Transactional
    public FinancialIdempotencyRecord complete(long recordId, int responseStatus, String responseBody) {
        FinancialIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        if ("COMPLETED".equals(record.getState())) {
            return record;
        }
        record.complete(responseStatus, responseBody, LocalDateTime.now(ZoneOffset.UTC));
        return repository.save(record);
    }

    @Transactional
    public FinancialIdempotencyRecord fail(long recordId) {
        FinancialIdempotencyRecord record = repository.findById(recordId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        record.fail(LocalDateTime.now(ZoneOffset.UTC));
        return repository.save(record);
    }

    private BeginResult existingResult(FinancialIdempotencyRecord existing, String hash) {
        if (!MessageDigest.isEqual(existing.getRequestHash().getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8))) {
            throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if ("COMPLETED".equals(existing.getState())) {
            return new Replay(existing.getId(), existing.getResponseStatus(), existing.getResponseBody());
        }
        if ("FAILED".equals(existing.getState())) {
            return new RetryableFailure(existing.getId());
        }
        return new InProgress(existing.getId(), existing.getCorrelationId());
    }

    private void validate(BeginCommand command) {
        if (command == null || blank(command.context()) || blank(command.operation())
                || blank(command.scopeKey()) || blank(command.idempotencyKey())) {
            throw new IllegalArgumentException("Financial idempotency identity is required");
        }
        if (command.context().equals("PROPERTY_COMMERCE")
                && command.hotelId() == null
                && !command.operation().startsWith("RESERVATION_")) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
    }

    private String normalize(String value) {
        String normalized = value.trim();
        if (normalized.length() > 160) {
            throw new IllegalArgumentException("Idempotency key is too long");
        }
        return normalized;
    }

    private String correlation(String value) {
        if (blank(value)) return UUID.randomUUID().toString();
        return value.replaceAll("[^A-Za-z0-9._:-]", "-").substring(0, Math.min(100, value.length()));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String hash(Object payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(canonicalize(objectMapper.valueToTree(payload)));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException("Unable to hash the financial payload", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) return node;
        if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> array.add(canonicalize(item)));
            return array;
        }
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.naturalOrder());
        for (String name : names) object.set(name, canonicalize(node.get(name)));
        return object;
    }

    public record BeginCommand(String context, String operation, String scopeKey, String idempotencyKey,
                               Object payload, Long hotelId, Long ownerUserId, String correlationId) {
    }

    public sealed interface BeginResult permits Acquired, Replay, InProgress, RetryableFailure {
        long recordId();
    }

    public record Acquired(FinancialIdempotencyRecord record) implements BeginResult {
        @Override
        public long recordId() { return record.getId(); }
    }

    public record Replay(long recordId, Integer responseStatus, String responseBody) implements BeginResult {
    }

    public record InProgress(long recordId, String correlationId) implements BeginResult {
    }

    public record RetryableFailure(long recordId) implements BeginResult {
    }
}

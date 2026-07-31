package com.hotel.paymentprovider.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FinancialAuditService {

    private static final String REDACTED = "[REDACTED]";
    private final FinancialAuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public FinancialAuditService(FinancialAuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FinancialAuditEvent append(AuditCommand command) {
        validate(command);
        String metadata = redactMetadata(command.metadata());
        FinancialAuditEvent event = new FinancialAuditEvent(
                command.context(), command.hotelId(), command.aggregateType(), command.aggregateId(),
                command.actorType(), command.actorId(), command.source(), command.previousState(),
                command.newState(), limit(command.reason(), 1000), limit(command.idempotencyIdentity(), 200),
                limit(command.providerIdentity(), 200), correlation(command.correlationId()), metadata,
                LocalDateTime.now(ZoneOffset.UTC));
        return repository.save(event);
    }

    private void validate(AuditCommand command) {
        if (command == null || command.context() == null || command.context().isBlank()) {
            throw new IllegalArgumentException("Audit context is required");
        }
        if ("PROPERTY_COMMERCE".equals(command.context()) && command.hotelId() == null) {
            throw new IllegalArgumentException("Property audit events require hotel ownership");
        }
        if (command.aggregateType() == null || command.aggregateType().isBlank()
                || command.aggregateId() == null || command.aggregateId().isBlank()) {
            throw new IllegalArgumentException("Audit aggregate identity is required");
        }
    }

    private String redactMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        JsonNode node = objectMapper.valueToTree(metadata);
        redactNode(node);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return "{\"metadata\":\"[REDACTED]\"}";
        }
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    objectNode.put(field.getKey(), REDACTED);
                } else {
                    redactNode(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redactNode);
        }
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("secret")
                || normalized.contains("token") || normalized.contains("signature")
                || normalized.contains("authorization") || normalized.contains("credential")
                || normalized.equals("key") || normalized.endsWith("key");
    }

    private String correlation(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return limit(value.replaceAll("[^A-Za-z0-9._:-]", "-"), 100);
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record AuditCommand(String context, Long hotelId, String aggregateType, String aggregateId,
                               String actorType, Long actorId, String source, String previousState,
                               String newState, String reason, String idempotencyIdentity,
                               String providerIdentity, String correlationId, Map<String, ?> metadata) {
    }
}

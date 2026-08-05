package com.hotel.paymentprovider.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hotel.dtos.FinancialAuditEventDTO;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.services.PropertyAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinancialAuditQueryService {
    public static final int RETENTION_DAYS = 2555;
    public static final int EXPORT_MAX_ROWS = 10_000;
    private static final String REDACTED = "[REDACTED]";

    private final FinancialAuditEventRepository repository;
    private final PropertyAccessService propertyAccessService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<FinancialAuditEventDTO> search(Query query, Pageable pageable) {
        Query safe = query == null ? new Query(null, null, null, null, null, null, null, null) : query;
        boolean systemAdmin = propertyAccessService.isSystemAdministrator();
        Set<Long> assigned = systemAdmin ? Set.of() : propertyAccessService.assignedHotelIds();
        if (!systemAdmin && "PLATFORM_BILLING".equalsIgnoreCase(safe.context())) throw notFound();
        if (!systemAdmin && safe.hotelId() != null && !assigned.contains(safe.hotelId())) throw notFound();
        if (!systemAdmin && assigned.isEmpty()) return Page.empty(pageable);

        Specification<FinancialAuditEvent> specification = Specification.where(null);
        if (!systemAdmin) {
            specification = specification.and((root, ignored, cb) -> cb.and(
                    cb.equal(root.get("context"), "PROPERTY_COMMERCE"), root.get("hotelId").in(assigned)));
        } else if (safe.hotelId() != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("hotelId"), safe.hotelId()));
        }
        specification = equal(specification, "context", safe.context(), true);
        specification = equal(specification, "aggregateType", safe.aggregateType(), true);
        specification = equal(specification, "aggregateId", safe.aggregateId(), false);
        specification = equal(specification, "source", safe.source(), true);
        specification = equal(specification, "correlationId", safe.correlationId(), false);
        if (safe.from() != null) specification = specification.and((root, ignored, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), safe.from()));
        if (safe.to() != null) specification = specification.and((root, ignored, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), safe.to()));
        return repository.findAll(specification, pageable).map(this::dto);
    }

    @Transactional(readOnly = true)
    public byte[] export(Query query) {
        var page = search(query, PageRequest.of(0, EXPORT_MAX_ROWS, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        StringBuilder csv = new StringBuilder("id,context,hotelId,aggregateType,aggregateId,actorType,actorId,source,previousState,newState,reason,idempotencyReference,providerReference,correlationId,metadataJson,occurredAt\r\n");
        page.forEach(event -> csv.append(row(event)).append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public Policy policy() { return new Policy(true, RETENTION_DAYS, EXPORT_MAX_ROWS, "REDACT_SECRETS_AND_PII_HASH_EXTERNAL_IDENTITIES"); }

    private FinancialAuditEventDTO dto(FinancialAuditEvent event) {
        return new FinancialAuditEventDTO(event.getId(), event.getContext(), event.getHotelId(), event.getAggregateType(),
                event.getAggregateId(), event.getActorType(), event.getActorId(), event.getSource(), event.getPreviousState(),
                event.getNewState(), event.getReason(), reference(event.getIdempotencyIdentity()), reference(event.getProviderIdentity()),
                event.getCorrelationId(), redact(event.getMetadataJson()), event.getOccurredAt());
    }

    private String redact(String json) {
        if (json == null || json.isBlank()) return "{}";
        try {
            JsonNode node = objectMapper.readTree(json); redactNode(node); return objectMapper.writeValueAsString(node);
        } catch (Exception exception) { return "{\"metadata\":\"[REDACTED]\"}"; }
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fieldNames().forEachRemaining(key -> { if (sensitive(key)) object.put(key, REDACTED); else redactNode(object.get(key)); });
        } else if (node instanceof ArrayNode array) array.forEach(this::redactNode);
    }

    private boolean sensitive(String key) {
        String value = key.toLowerCase(Locale.ROOT).replace("_", "");
        return value.contains("password") || value.contains("secret") || value.contains("token") || value.contains("signature")
                || value.contains("credential") || value.contains("authorization") || value.contains("accountnumber")
                || value.equals("email") || value.equals("phone") || value.equals("address") || value.equals("fullname");
    }

    private String reference(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("sha256:");
            for (int index = 0; index < 8; index++) result.append(String.format(Locale.ROOT, "%02x", hash[index]));
            return result.toString();
        } catch (Exception exception) { return REDACTED; }
    }

    private String row(FinancialAuditEventDTO event) {
        return String.join(",", quote(event.id()), quote(event.context()), quote(event.hotelId()), quote(event.aggregateType()),
                quote(event.aggregateId()), quote(event.actorType()), quote(event.actorId()), quote(event.source()),
                quote(event.previousState()), quote(event.newState()), quote(event.reason()), quote(event.idempotencyReference()),
                quote(event.providerReference()), quote(event.correlationId()), quote(event.metadataJson()), quote(event.occurredAt()));
    }

    private String quote(Object value) {
        if (value == null) return ""; String text = value.toString().replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text + "\"";
    }

    private Specification<FinancialAuditEvent> equal(Specification<FinancialAuditEvent> current, String field, String value, boolean upper) {
        if (value == null || value.isBlank()) return current; String normalized = upper ? value.trim().toUpperCase(Locale.ROOT) : value.trim();
        return current.and((root, ignored, cb) -> cb.equal(root.get(field), normalized));
    }

    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Financial audit scope not found."); }

    public record Query(String context, Long hotelId, String aggregateType, String aggregateId, String source,
                        String correlationId, LocalDateTime from, LocalDateTime to) { }
    public record Policy(boolean appendOnly, int retentionDays, int exportMaxRows, String redactionPolicy) { }
}

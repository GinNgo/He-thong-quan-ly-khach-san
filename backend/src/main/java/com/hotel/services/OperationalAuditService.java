package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hotel.dtos.OperationalAuditEventDTO;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.OperationalAuditEventRepository;
import com.hotel.security.CustomUserDetails;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationalAuditService {

    private static final String REDACTED = "[REDACTED]";
    private final OperationalAuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock;

    public OperationalAuditService(OperationalAuditEventRepository repository, ObjectMapper objectMapper,
                                   PropertyAccessService propertyAccessService) {
        this(repository, objectMapper, propertyAccessService, Clock.systemUTC());
    }

    public OperationalAuditService(OperationalAuditEventRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null, Clock.systemUTC());
    }

    OperationalAuditService(OperationalAuditEventRepository repository, ObjectMapper objectMapper,
                            PropertyAccessService propertyAccessService, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.propertyAccessService = propertyAccessService;
        this.clock = clock;
    }

    @Transactional
    public OperationalAuditEvent append(AuditCommand command) {
        AuditCommand normalized = normalize(command);
        OperationalAuditEvent event = new OperationalAuditEvent(
                normalized.scope(), normalized.hotelId(), normalized.domain(), normalized.eventType(),
                normalized.aggregateType(), normalized.aggregateId(), normalized.actorType(), normalized.actorId(),
                limit(normalized.reason(), 500), toJson(normalized.beforeState()), toJson(normalized.afterState()),
                correlation(normalized.correlationId()), LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        return repository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<OperationalAuditEventDTO> search(AuditQuery query, Pageable pageable) {
        AuditQuery safeQuery = query == null ? new AuditQuery(null, null, null, null, null, null, null, null, null, null) : query;
        boolean systemAdministrator = propertyAccessService == null || propertyAccessService.isSystemAdministrator();
        Set<Long> allowedHotelIds = systemAdministrator ? Set.of() : propertyAccessService.assignedHotelIds();
        if (!systemAdministrator && "SYSTEM".equalsIgnoreCase(safeQuery.scope())) {
            throw new ResourceNotFoundException("Audit scope not found.");
        }
        if (!systemAdministrator && safeQuery.hotelId() != null && !allowedHotelIds.contains(safeQuery.hotelId())) {
            throw new ResourceNotFoundException("Audit property not found.");
        }
        if (!systemAdministrator && allowedHotelIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<OperationalAuditEvent> specification = Specification.where(null);
        if (systemAdministrator && safeQuery.hotelId() != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("hotelId"), safeQuery.hotelId()));
        }
        if (!systemAdministrator) {
            specification = specification.and((root, ignored, cb) -> root.get("hotelId").in(allowedHotelIds));
        }
        if (safeQuery.scope() != null && !safeQuery.scope().isBlank()) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("scope"), safeQuery.scope().trim().toUpperCase(Locale.ROOT)));
        }
        specification = addEqual(specification, "domain", safeQuery.domain());
        specification = addEqual(specification, "eventType", safeQuery.eventType());
        specification = addEqual(specification, "aggregateType", safeQuery.aggregateType());
        specification = addEqual(specification, "aggregateId", safeQuery.aggregateId());
        if (safeQuery.actorId() != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("actorId"), safeQuery.actorId()));
        }
        if (safeQuery.correlationId() != null && !safeQuery.correlationId().isBlank()) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("correlationId"), safeQuery.correlationId().trim()));
        }
        if (safeQuery.from() != null) {
            specification = specification.and((root, ignored, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), safeQuery.from()));
        }
        if (safeQuery.to() != null) {
            specification = specification.and((root, ignored, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), safeQuery.to()));
        }
        return repository.findAll(specification, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(AuditQuery query) {
        Page<OperationalAuditEventDTO> page = search(query, org.springframework.data.domain.PageRequest.of(
                0, 10_000, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("occurredAt"),
                        org.springframework.data.domain.Sort.Order.desc("id"))));
        StringBuilder csv = new StringBuilder("\uFEFFid,scope,hotelId,domain,eventType,aggregateType,aggregateId,actorType,actorId,reason,beforeState,afterState,correlationId,occurredAt\r\n");
        page.getContent().forEach(event -> csv.append(csvRow(event)).append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvRow(OperationalAuditEventDTO event) {
        return String.join(",", quote(event.id()), quote(event.scope()), quote(event.hotelId()), quote(event.domain()),
                quote(event.eventType()), quote(event.aggregateType()), quote(event.aggregateId()), quote(event.actorType()),
                quote(event.actorId()), quote(event.reason()), quote(event.beforeState()), quote(event.afterState()),
                quote(event.correlationId()), quote(event.occurredAt()));
    }

    private String quote(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text + "\"";
    }

    private Specification<OperationalAuditEvent> addEqual(Specification<OperationalAuditEvent> current, String field, String value) {
        if (value == null || value.isBlank()) return current;
        String normalized = "aggregateId".equals(field) ? value.trim() : value.trim().toUpperCase(Locale.ROOT);
        return current.and((root, ignored, cb) -> cb.equal(root.get(field), normalized));
    }

    private OperationalAuditEventDTO toDto(OperationalAuditEvent event) {
        return new OperationalAuditEventDTO(event.getId(), event.getScope(), event.getHotelId(), event.getDomain(),
                event.getEventType(), event.getAggregateType(), event.getAggregateId(), event.getActorType(),
                event.getActorId(), event.getReason(), event.getBeforeStateJson(), event.getAfterStateJson(),
                event.getCorrelationId(), event.getOccurredAt());
    }

    private AuditCommand normalize(AuditCommand command) {
        if (command == null) throw new IllegalArgumentException("Audit command is required.");
        String scope = upper(command.scope());
        if (!Set.of("TENANT", "SYSTEM").contains(scope)) throw new IllegalArgumentException("Audit scope is invalid.");
        if (("TENANT".equals(scope) && command.hotelId() == null) || ("SYSTEM".equals(scope) && command.hotelId() != null)) {
            throw new IllegalArgumentException("Audit scope and property ownership do not match.");
        }
        requireText(command.domain(), "Audit domain");
        requireText(command.eventType(), "Audit event type");
        requireText(command.aggregateType(), "Audit aggregate type");
        requireText(command.aggregateId(), "Audit aggregate id");
        requireText(command.reason(), "Audit reason");
        Actor actor = resolveActor(command.actorType(), command.actorId());
        return new AuditCommand(scope, command.hotelId(), upper(command.domain()), upper(command.eventType()),
                upper(command.aggregateType()), limit(command.aggregateId(), 100), actor.type(), actor.id(),
                command.reason().trim(), command.beforeState(), command.afterState(), command.correlationId());
    }

    private Actor resolveActor(String actorType, Long actorId) {
        if (actorId != null || (actorType != null && !actorType.isBlank())) {
            return new Actor(actorType == null || actorType.isBlank() ? "USER" : upper(actorType), actorId);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return new Actor("USER", details.getUserId());
        }
        return new Actor("SYSTEM", null);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        JsonNode node = value instanceof JsonNode jsonNode ? jsonNode.deepCopy() : objectMapper.valueToTree(value);
        redactNode(node);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return "{\"state\":\"[REDACTED]\"}";
        }
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(key -> {
                if (isSensitive(key)) objectNode.put(key, REDACTED);
                else redactNode(objectNode.get(key));
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redactNode);
        }
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("signature") || normalized.contains("authorization")
                || normalized.contains("credential") || normalized.equals("key") || normalized.endsWith("key")
                || normalized.contains("account_number") || normalized.contains("accountnumber")
                || normalized.equals("phone") || normalized.equals("email") || normalized.equals("address");
    }

    private String correlation(String value) {
        String candidate = value;
        if (candidate == null || candidate.isBlank()) candidate = MDC.get("correlationId");
        if (candidate == null || candidate.isBlank()) candidate = UUID.randomUUID().toString();
        return limit(candidate.replaceAll("[^A-Za-z0-9._:-]", "-"), 100);
    }

    private String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private String limit(String value, int max) { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }
    private void requireText(String value, String label) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required."); }

    public record AuditCommand(String scope, Long hotelId, String domain, String eventType, String aggregateType,
                               String aggregateId, String actorType, Long actorId, String reason,
                               Object beforeState, Object afterState, String correlationId) { }

    public record AuditQuery(String scope, Long hotelId, String domain, String eventType, String aggregateType,
                              String aggregateId, Long actorId, String correlationId, LocalDateTime from,
                              LocalDateTime to) { }

    private record Actor(String type, Long id) { }
}
